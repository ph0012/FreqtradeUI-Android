package com.freqtradeui.android.ssh

import android.util.Log
import com.freqtradeui.android.data.models.ConnectionEvent
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.models.ConnectionStateMachine
import com.freqtradeui.android.data.models.FailureKind
import com.freqtradeui.android.data.models.ProfileSecret
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.security.HostKeyFingerprint
import com.freqtradeui.android.security.HostKeyStore
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Chave de host aguardando decisao TOFU do usuario.
 *
 * @param host endereco consultado.
 * @param keyType algoritmo da chave (ex.: `ssh-ed25519`).
 * @param fingerprint impressao digital SHA-256, para exibicao na UI.
 * @param keyBlob bytes crus da chave, gravados so se o usuario aprovar.
 */
data class PendingHostKey(
    val host: String,
    val keyType: String,
    val fingerprint: String,
    val keyBlob: ByteArray,
) {
    // Igualdade por conteudo: ByteArray usa identidade por padrao.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingHostKey) return false
        return host == other.host &&
            keyType == other.keyType &&
            fingerprint == other.fingerprint &&
            keyBlob.contentEquals(other.keyBlob)
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + keyType.hashCode()
        result = 31 * result + fingerprint.hashCode()
        result = 31 * result + keyBlob.contentHashCode()
        return result
    }
}

/**
 * Coordena os tuneis SSH do aplicativo.
 *
 * Propositalmente sem interface Android: nao conhece Activity, Service nem
 * notificacao. A UI observa [state] e a UI ou o servico chamam os comandos.
 * Assim a mesma instancia serve aos dois, e o estado exibido e o estado real.
 *
 * Arquitetura:
 * - Um unico [ConnectionStateMachine] decide o que fazer; a classe so traduz
 *   cada efeito em operacoes reais.
 * - Um unico laco de conexao por vez. [connect] chamado duas vezes nao cria
 *   duas sessoes: o laco anterior e cancelado primeiro.
 * - Reconexao por backoff exponencial com jitter, cancelavel.
 * - Monitoramento real: keepalive SSH **e** teste TCP na porta local, porque
 *   uma sessao pode estar aberta com o encaminhamento morto.
 *
 * @param hostKeyStore chaves de host confiadas (TOFU).
 */
class SshTunnelManager(
    private val hostKeyStore: HostKeyStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val machine = ConnectionStateMachine()

    private var session: Session? = null
    private var loopJob: Job? = null
    private var retryJob: Job? = null
    private var monitorJob: Job? = null

    private var activeProfile: ConnectionProfile? = null
    private var activeSecret: ProfileSecret? = null
    private var lastHostKeyMismatch = false

    @Volatile
    private var pending: PendingHostKey? = null

    private val _state = MutableStateFlow(machine.state)

    /** Estado observavel do tunel; espelha a maquina de estados. */
    val state: StateFlow<ConnectionSnapshot> = _state.asStateFlow()

    /** Chave de host aguardando aprovacao do usuario, quando houver. */
    val pendingHostKey: PendingHostKey? get() = pending

    /**
     * Inicia (ou reinicia) o laco de conexao para o perfil.
     *
     * Chamar repetidamente e seguro: cancela a tentativa anterior antes de
     * comecar, entao nunca ha duas sessoes para o mesmo perfil.
     */
    fun connect(profile: ConnectionProfile, secret: ProfileSecret) {
        activeProfile = profile
        activeSecret = secret
        pending = null
        lastHostKeyMismatch = false

        // Cancela o laco anterior antes de comecar outro.
        loopJob?.cancel()
        retryJob?.cancel()
        closeResources()

        // O efeito OpenTunnel dispara startLoop(); chamar de novo cancelaria
        // a propria tentativa em curso.
        publish(machine.reduce(ConnectionEvent.Connect(profile.id)))
    }

    /**
     * Encerra a conexao. Marca o encerramento como intencional, de modo que a
     * queda da sessao nao seja interpretada como falha transitoria.
     */
    fun disconnect() {
        publish(machine.reduce(ConnectionEvent.Disconnect))
    }

    /** Libera todos os recursos; usado no encerramento do processo. */
    fun shutdown() {
        loopJob?.cancel()
        retryJob?.cancel()
        monitorJob?.cancel()
        closeResources()
        scope.cancel()
    }

    /**
     * Aprova a chave de host pendente e a grava como confiavel.
     *
     * So deve ser chamado apos o usuario conferir a impressao digital.
     */
    fun approveHostKey() {
        val key = pending ?: return
        hostKeyStore.trustKey(key.host, key.keyBlob)
        pending = null
        lastHostKeyMismatch = false
        val profile = activeProfile ?: return
        val secret = activeSecret ?: return
        // Reconecta com a mesma configuracao; nada e alterado em silencioso.
        loopJob?.cancel()
        retryJob?.cancel()
        closeResources()
        // O efeito OpenTunnel dispara startLoop() automaticamente.
        publish(machine.reduce(ConnectionEvent.Connect(profile.id)))
    }

    /** Descarta a chave pendente sem confiar nela. */
    fun rejectHostKey() {
        pending = null
        // Limpa a impressao digital publicada; sem isso o dialogo TOFU
        // permaneceria na tela mesmo apos a recusa.
        _state.value = _state.value.copy(pendingHostKey = null)
    }

    /**
     * Sai do estado Connecting e publica a chave pendente para a UI.
     *
     * Sem esta publicacao o dialogo TOFU nunca aparece: a maquina sairia para
     * Disconnected, mas o snapshot nao carregaria a impressao digital — e o
     * usuario ficaria esperando "Conectando..." sem nada na tela.
     */
    private fun publishPendingHostKey() {
        val key = pending ?: return
        val transition = machine.reduce(
            ConnectionEvent.Failed(FailureKind.UnknownHostKey, null),
        )
        _state.value = transition.state.copy(pendingHostKey = key.fingerprint)
    }

    private fun startLoop() {
        loopJob?.cancel()
        loopJob = scope.launch {
            val profile = activeProfile ?: return@launch
            val secret = activeSecret ?: return@launch

            Log.i(TAG, "Conectando a ${profile.host}:${profile.sshPort} (${profile.username})")

            try {
                openTunnel(profile, secret)

                // Chave pendente: a maquina ja saiu para Disconnected.
                if (pending != null) {
                    publishPendingHostKey()
                    return@launch
                }

                Log.i(
                    TAG,
                    "Tunel ativo: 127.0.0.1:${profile.localPort} -> " +
                        "${profile.remoteHost}:${profile.remotePort}",
                )
                publish(machine.reduce(ConnectionEvent.Connected(profile.localPort)))
                startMonitor()
            } catch (e: Exception) {
                // Chave desconhecida/alterada: publica a decisao pendente em
                // vez de deixar o estado preso em Connecting.
                if (pending != null) {
                    publishPendingHostKey()
                    return@launch
                }
                val kind = classify(e, lastHostKeyMismatch)
                publish(
                    machine.reduce(ConnectionEvent.Failed(kind, describeError(kind, e))),
                )
            }
        }
    }

    private suspend fun openTunnel(profile: ConnectionProfile, secret: ProfileSecret) {
        val jsch = JSch().apply {
            addIdentity(
                "key-${profile.id}",
                secret.privateKey.toByteArray(Charsets.UTF_8),
                null,
                secret.passphrase?.toByteArray(Charsets.UTF_8),
            )
        }

        val newSession = jsch.getSession(profile.username, profile.host, profile.sshPort).apply {
            // Politica de chaves de host e a nossa (TofuRepository): NOT_INCLUDED
            // precisa virar erro, nunca aceitacao automatica.
            setConfig("StrictHostKeyChecking", "yes")
            // Somente chave publica nesta etapa, conforme especificado.
            setConfig("PreferredAuthentications", "publickey")
            hostKeyRepository = TofuRepository()

            // Keepalive: detecta conexao morta mesmo sem trafego do usuario.
            setServerAliveInterval(KEEPALIVE_INTERVAL_MS)
            setServerAliveCountMax(KEEPALIVE_COUNT_MAX)
            // Tempo limite de estabelecimento evita travar em rede ruim.
            connect(CONNECT_TIMEOUT_MS)
        }
        session = newSession

        // Falha se a porta local ja estiver em uso: erro de configuracao,
        // nao transitorio. Devolve a porta efetivamente vinculada.
        try {
            newSession.setPortForwardingL(
                profile.localPort,
                profile.remoteHost,
                profile.remotePort,
            )
        } catch (e: JSchException) {
            throw IllegalStateException("local port already in use: ${e.message}", e)
        }
    }

    /**
     * Monitora a saude do tunel.
     *
     * Alem do keepalive SSH, testa de fato o encaminhamento local com um TCP
     * contra a porta local: uma sessao pode estar aberta com o canal morto, e
     * o app nao pode depender de socket aberto para dizer que esta conectado.
     * O teste nao autentica na API do Freqtrade.
     */
    private fun startMonitor() {
        monitorJob?.cancel()
        val profile = activeProfile ?: return
        monitorJob = scope.launch {
            while (isActive) {
                delay(HEALTH_CHECK_INTERVAL_MS)
                val current = session
                val healthy = current != null &&
                    current.isConnected &&
                    isLocalPortAccepting(profile.localPort)
                if (!healthy) {
                    publish(
                        machine.reduce(
                            ConnectionEvent.TunnelLost("tunel indisponivel"),
                        ),
                    )
                    return@launch
                }
            }
        }
    }

    /**
     * Abre e fecha uma conexao TCP na porta local apenas para saber se o
     * encaminhamento responde. Nao envia nem le dados da API.
     */
    private fun isLocalPortAccepting(port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), TCP_PROBE_TIMEOUT_MS)
            true
        }
    } catch (e: IOException) {
        false
    }

    /**
     * Executa o efeito pedido pela maquina. E o unico lugar que reage a uma
     * transicao, o que mantem as regras de estado em um ponto so.
     */
    private fun publish(transition: com.freqtradeui.android.data.models.Transition) {
        _state.value = transition.state
        when (val effect = transition.effect) {
            is com.freqtradeui.android.data.models.ConnectionEffect.OpenTunnel -> {
                startLoop()
            }

            is com.freqtradeui.android.data.models.ConnectionEffect.ScheduleRetry -> {
                scheduleRetry(effect.delayMs)
            }

            com.freqtradeui.android.data.models.ConnectionEffect.CloseTunnel -> {
                monitorJob?.cancel()
                closeResources()
            }

            com.freqtradeui.android.data.models.ConnectionEffect.StopService -> {
                closeResources()
            }

            com.freqtradeui.android.data.models.ConnectionEffect.None -> Unit
        }
    }

    private fun scheduleRetry(delayMs: Long) {
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(delayMs)
            publish(machine.reduce(ConnectionEvent.RetryDue))
        }
    }

    private fun closeResources() {
        runCatching { session?.disconnect() }
        session = null
    }

    private fun classify(e: Exception, hostKeyChanged: Boolean): FailureKind {
        if (hostKeyChanged) return FailureKind.ChangedHostKey
        // Excecoes tipadas do JSch sao mais confiaveis que o texto da
        // mensagem, que varia entre versoes e localizacoes.
        return when (e) {
            is com.jcraft.jsch.JSchChangedHostKeyException -> FailureKind.ChangedHostKey
            is com.jcraft.jsch.JSchUnknownHostKeyException -> FailureKind.UnknownHostKey
            else -> com.freqtradeui.android.data.models.ConnectionErrorClassifier
                .classify(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Mensagem de erro para a UI. O log recebe a classe da excecao, nunca o
     * conteudo de credenciais.
     */
    private fun describeError(kind: FailureKind, e: Exception): String {
        Log.w(TAG, "Falha de conexao (${e.javaClass.simpleName})")
        return com.freqtradeui.android.data.models.ConnectionErrorClassifier
            .userMessage(kind, e.message)
    }

    /**
     * Repositorio de chaves de host Trust On First Use.
     *
     * - Host nunca visto: recusa e sinaliza chave pendente, para o usuario
     *   aprovar **depois** de conferir a impressao digital.
     * - Host aprovado com a mesma chave: aceita.
     * - Host aprovado com chave diferente: recusa sem oferecer aprovacao. E a
     *   hipotese de man-in-the-middle e nunca e resolvida automaticamente.
     */
    private inner class TofuRepository : HostKeyRepository {

        override fun check(hostname: String?, key: ByteArray?): Int {
            if (key == null || hostname == null) return HostKeyRepository.NOT_INCLUDED
            val trusted = hostKeyStore.getTrustedKey(hostname)

            // `when` sem sujeito: as condições são booleanas. Com sujeito, a
            // comparação seria `trusted == <boolean>` — tipo incompatível.
            return when {
                trusted == null -> {
                    pending = PendingHostKey(
                        host = hostname,
                        keyType = HostKeyFingerprint.algorithm(key),
                        fingerprint = HostKeyFingerprint.sha256(key),
                        keyBlob = key,
                    )
                    HostKeyRepository.NOT_INCLUDED
                }

                // Compara os bytes da chave em tempo constante.
                HostKeyFingerprint.constantTimeEquals(trusted, key) -> HostKeyRepository.OK

                else -> {
                    lastHostKeyMismatch = true
                    // Distingue "mudou" de "desconhecida": a primeira e a
                    // hipotese de ataque e nao recebe opcao de aprovacao.
                    HostKeyRepository.CHANGED
                }
            }
        }

        override fun add(
            hostkey: com.jcraft.jsch.HostKey?,
            ui: com.jcraft.jsch.UserInfo?,
        ) {
            // Aprovacao so por approveHostKey(), apos confirmacao do usuario.
        }

        override fun remove(hostname: String?, type: String?) {
            hostname?.let { hostKeyStore.remove(it) }
        }

        override fun remove(hostname: String?, type: String?, key: ByteArray?) {
            hostname?.let { hostKeyStore.remove(it) }
        }

        override fun getKnownHostsRepositoryID(): String = "freqtrade-tofu"

        override fun getHostKey(): Array<com.jcraft.jsch.HostKey> = emptyArray()

        override fun getHostKey(
            host: String?,
            type: String?,
        ): Array<com.jcraft.jsch.HostKey> = emptyArray()
    }

    companion object {
        private const val TAG = "SshTunnelManager"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val HEALTH_CHECK_INTERVAL_MS = 30_000L
        private const val TCP_PROBE_TIMEOUT_MS = 3_000
        // JSch espera MILISSEGUNDOS: um valor em segundos (30) mataria a
        // sessao apos ~90 ms sem resposta do servidor.
        private const val KEEPALIVE_INTERVAL_MS = 30_000
        private const val KEEPALIVE_COUNT_MAX = 3

        /** Estado desconectado padrao, usado antes de qualquer conexao. */
        val idleState: ConnectionSnapshot = ConnectionSnapshot(status = TunnelStatus.Disconnected)
    }
}
