package com.freqtradeui.android.ssh

import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.JSchHostKeyException
import com.jcraft.jsch.Session
import com.jcraft.jsch.UserInfo
import com.freqtradeui.android.data.models.ConnectionState
import com.freqtradeui.android.security.HostKeyFingerprint
import com.freqtradeui.android.security.HostKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Exceção lançada quando o servidor apresenta uma chave de host ainda não
 * confiada. A interface deve oferecer a decisão TOFU ao usuário.
 */
class UnknownHostKeyException(val pending: PendingHostKey) :
    Exception("Chave de host não reconhecida: ${pending.host} (${pending.keyType}).")

/**
 * Gerenciador do túnel SSH local.
 *
 * - Autentica por chave privada.
 * - Verifica a chave de host (Trust On First Use): na primeira conexão exige
 *   aprovação manual; uma chave diferente para um host já confiado é recusada
 *   (proteção contra man-in-the-middle).
 * - Encaminha a porta local [SshConfig.localPort] para `127.0.0.1:8080` na VPS.
 * - Expõe o estado via [state] para a UI reagir sem refatoração.
 *
 * Toda operação de rede roda em [Dispatchers.IO]. As credenciais ficam em
 * memória apenas durante a conexão e nunca são registradas em log.
 *
 * @param hostKeyStore repositório de chaves de host confiáveis.
 */
class SshTunnelManager(
    private val hostKeyStore: HostKeyStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(TunnelState())
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private val mutex = Mutex()

    @Volatile private var session: Session? = null
    @Volatile private var currentConfig: SshConfig? = null
    @Volatile private var pendingKey: PendingHostKey? = null
    @Volatile private var lastKeyStatus: Int = HostKeyRepository.NOT_INCLUDED

    /**
     * Estabelece o túnel SSH e o encaminhamento de porta local.
     *
     * @return [Result.success] com a porta local encaminhada, ou [Result.failure]
     *   com a causa. Em chave de host desconhecida, falha com
     *   [UnknownHostKeyException] e expõe [TunnelState.pendingHostKey] à UI.
     */
    suspend fun connect(config: SshConfig): Result<Int> = mutex.withLock {
        withContext(Dispatchers.IO) {
            currentConfig = config
            pendingKey = null
            lastKeyStatus = HostKeyRepository.NOT_INCLUDED
            _state.value = TunnelState(
                ssh = ConnectionState.Connecting,
                message = "Conectando a ${config.host}:${config.sshPort}...",
            )
            try {
                val jsch = JSch()
                jsch.addIdentity(
                    IDENTITY_NAME,
                    config.privateKey.toByteArray(Charsets.UTF_8),
                    null,
                    config.privateKeyPassphrase?.toByteArray(Charsets.UTF_8),
                )
                jsch.hostKeyRepository = TofuHostKeyRepository()

                val newSession = jsch.getSession(config.username, config.host, config.sshPort)
                newSession.setConfig("StrictHostKeyChecking", "yes")
                newSession.connect(CONNECT_TIMEOUT_MS)

                val localPort = newSession.setPortForwardingL(
                    config.localPort,
                    config.remoteHost,
                    config.remotePort,
                )
                session = newSession
                _state.value = TunnelState(
                    ssh = ConnectionState.Connected,
                    localPort = localPort,
                    message = "Túnel ativo: 127.0.0.1:$localPort → " +
                        "${config.remoteHost}:${config.remotePort}",
                )
                Result.success(localPort)
            } catch (e: JSchException) {
                failConnection(e)
            } catch (e: Exception) {
                _state.value = TunnelState(
                    ssh = ConnectionState.Error,
                    message = e.message ?: "Erro inesperado na conexão.",
                )
                Result.failure(e)
            }
        }
    }

    /**
     * Traduz a falha de conexão em estado de UI.
     *
     * - Chave de host ainda não confiada → expõe [TunnelState.pendingHostKey]
     *   para decisão do usuário (TOFU).
     * - Chave de host alterada → erro grave, **sem** opção de aprovação
     *   (possível ataque man-in-the-middle).
     */
    private fun failConnection(e: JSchException): Result<Int> {
        val message = e.message.orEmpty()
        val pending = pendingKey
        val isHostKeyIssue = e is JSchHostKeyException ||
            message.contains("UnknownHostKey", ignoreCase = true) ||
            message.contains("HostKey", ignoreCase = true)

        return if (isHostKeyIssue && lastKeyStatus == HostKeyRepository.NOT_INCLUDED && pending != null) {
            _state.value = TunnelState(
                ssh = ConnectionState.Disconnected,
                pendingHostKey = pending,
                message = "Chave de host não reconhecida. Confira a impressão digital e " +
                    "aprove para confiar.",
            )
            Result.failure(UnknownHostKeyException(pending))
        } else if (lastKeyStatus == HostKeyRepository.CHANGED) {
            _state.value = TunnelState(
                ssh = ConnectionState.Error,
                message = "A chave de host de ${pending?.host ?: currentConfig?.host} mudou. " +
                    "Conexão bloqueada por segurança.",
            )
            Result.failure(e)
        } else {
            _state.value = TunnelState(
                ssh = ConnectionState.Error,
                message = message.ifBlank { "Falha ao conectar." },
            )
            Result.failure(e)
        }
    }

    /**
     * Aprova a chave de host pendente (TOFU), persiste-a e reconecta.
     * Sem efeito se não houver decisão pendente.
     */
    fun approveHostKey() {
        val pending = pendingKey ?: return
        hostKeyStore.trustKey(pending.host, pending.keyBytes)
        pendingKey = null
        currentConfig?.let { config -> scope.launch { connect(config) } }
    }

    /**
     * Rejeita a chave de host pendente e encerra a tentativa de conexão.
     */
    fun rejectHostKey() {
        pendingKey = null
        _state.value = TunnelState(
            ssh = ConnectionState.Disconnected,
            message = "Conexão recusada: chave de host não confiável.",
        )
    }

    /**
     * Encerra o túnel SSH e libera a porta local.
     */
    fun disconnect() {
        runCatching { session?.disconnect() }
        session = null
        pendingKey = null
        _state.value = TunnelState()
    }

    /**
     * Encerra o túnel e o escopo de corrotinas (ex.: ao destruir a aplicação).
     */
    fun shutdown() {
        disconnect()
        scope.cancel()
    }

    /**
     * Repositório de chaves de host com política Trust On First Use.
     *
     * - Chave ausente → [HostKeyRepository.NOT_INCLUDED]: a conexão é abortada e
     *   a chave é oferecida ao usuário para decisão.
     * - Chave igual à confiada → [HostKeyRepository.OK].
     * - Chave diferente da confiada → [HostKeyRepository.CHANGED]: recusada,
     *   pois indica possível ataque man-in-the-middle.
     */
    private inner class TofuHostKeyRepository : HostKeyRepository {

        override fun check(host: String, key: ByteArray): Int {
            val trusted = hostKeyStore.getTrustedKey(host)
            pendingKey = PendingHostKey(
                host = host,
                fingerprint = HostKeyFingerprint.sha256(key),
                keyType = HostKeyFingerprint.algorithm(key),
                keyBytes = key,
            )
            lastKeyStatus = when {
                trusted == null -> HostKeyRepository.NOT_INCLUDED
                HostKeyFingerprint.constantTimeEquals(trusted, key) -> HostKeyRepository.OK
                else -> HostKeyRepository.CHANGED
            }
            return lastKeyStatus
        }

        override fun add(hostkey: HostKey?, ui: UserInfo?) = Unit

        override fun remove(host: String?, type: String?) = Unit

        override fun remove(host: String?, type: String?, key: ByteArray?) = Unit

        override fun getKnownHostsRepositoryID(): String = "freqtrade-hostkeys"

        override fun getHostKey(): Array<HostKey> = emptyArray()

        override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
    }

    private companion object {
        const val IDENTITY_NAME = "freqtrade_key"
        const val CONNECT_TIMEOUT_MS = 20_000
    }
}
