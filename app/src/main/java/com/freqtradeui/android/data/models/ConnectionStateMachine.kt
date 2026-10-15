package com.freqtradeui.android.data.models

/**
 * Eventos que alimentam a máquina de estados da conexão.
 */
sealed interface ConnectionEvent {
    /** Usuário pediu para conectar ao perfil. */
    data class Connect(val profileId: String) : ConnectionEvent

    /** Usuário pediu uma conexão direta, sem abrir túnel SSH. */
    data class ConnectDirect(val profileId: String) : ConnectionEvent

    /** Conexão concluída; a porta é nula para acesso direto sem túnel. */
    data class Connected(val localPort: Int?) : ConnectionEvent

    /** Tentativa falhou. */
    data class Failed(val kind: FailureKind, val message: String?) : ConnectionEvent

    /** O túnel ativo caiu (detectado pelo monitoramento). */
    data class TunnelLost(val message: String?) : ConnectionEvent

    /** Passou a espera do backoff; nova tentativa deve começar. */
    data object RetryDue : ConnectionEvent

    /** Usuário pediu para desconectar. */
    data object Disconnect : ConnectionEvent

    /** Recursos liberados; estado pode ser encerrado. */
    data object CleanupDone : ConnectionEvent
}

/**
 * Efeitos colaterais pedidos pela máquina. Quem a conduz (o serviço) executa
 * cada efeito; a máquina em si não faz I/O — o que a torna testável.
 */
sealed interface ConnectionEffect {
    /** Abrir a sessão SSH e o encaminhamento. */
    data object OpenTunnel : ConnectionEffect

    /** Agendar nova tentativa após [delayMs]. */
    data class ScheduleRetry(val delayMs: Long, val attempt: Int) : ConnectionEffect

    /** Cancelar a espera agendada e encerrar a sessão atual. */
    data object CloseTunnel : ConnectionEffect

    /** Parar o serviço: nada mais a fazer. */
    data object StopService : ConnectionEffect

    /** Nenhuma ação. */
    data object None : ConnectionEffect
}

/**
 * Resultado de uma transição: novo estado + efeito a executar.
 */
data class Transition(
    val state: ConnectionSnapshot,
    val effect: ConnectionEffect,
)

/**
 * Máquina de estados explícita da conexão SSH.
 *
 * Separa a lógica de decisão do I/O de rede: [reduce] é pura e determinística,
 * então as transições podem ser verificadas em testes de unidade. O serviço
 * apenas traduz cada [ConnectionEffect] em operações reais.
 *
 * Regras garantidas:
 * - `Disconnect` sempre leva a [TunnelStatus.Disconnected] e nunca reconecta,
 *   mesmo que o evento chegue no meio de uma espera.
 * - Falhas permanentes ([FailureKind.Authentication], [FailureKind.ChangedHostKey],
 *   [FailureKind.Configuration]) não geram nova tentativa.
 * - [FailureKind.UnknownHostKey] aguarda decisão do usuário.
 * - Há no máximo uma espera de reconexão em vigor por vez.
 *
 * @param policy política de backoff.
 */
class ConnectionStateMachine(
    private val policy: ReconnectPolicy = ReconnectPolicy(),
) {

    /** Estado corrente. */
    var state: ConnectionSnapshot = ConnectionSnapshot()
        private set

    /**
     * Aplica [event] ao estado atual.
     *
     * @return a transição resultante (estado atualizado + efeito).
     */
    fun reduce(event: ConnectionEvent): Transition = when (event) {
        is ConnectionEvent.Connect -> onConnect(event.profileId)
        is ConnectionEvent.ConnectDirect -> onDirectConnect(event.profileId)
        is ConnectionEvent.Connected -> onConnected(event.localPort)
        is ConnectionEvent.Failed -> onFailed(event.kind, event.message)
        is ConnectionEvent.TunnelLost -> onTunnelLost(event.message)
        ConnectionEvent.RetryDue -> onRetryDue()
        ConnectionEvent.Disconnect -> onDisconnect()
        ConnectionEvent.CleanupDone -> onCleanupDone()
    }

    private fun onConnect(profileId: String): Transition = commit(
        ConnectionSnapshot(
            status = TunnelStatus.Connecting,
            profileId = profileId,
            attempt = 0,
        ),
        ConnectionEffect.OpenTunnel,
    )

    private fun onDirectConnect(profileId: String): Transition = commit(
        ConnectionSnapshot(
            status = TunnelStatus.Connecting,
            profileId = profileId,
            attempt = 0,
        ),
        ConnectionEffect.None,
    )

    private fun onConnected(localPort: Int?): Transition = commit(
        state.copy(
            status = TunnelStatus.Connected,
            localPort = localPort,
            attempt = 0,
            nextRetryInMs = null,
            error = null,
        ),
        ConnectionEffect.None,
    )

    private fun onFailed(kind: FailureKind, message: String?): Transition {
        // Decisão pendente do usuário: nem erro terminal, nem retry.
        if (kind == FailureKind.UnknownHostKey) {
            return commit(
                state.copy(
                    status = TunnelStatus.Disconnected,
                    nextRetryInMs = null,
                    error = ConnectionErrorClassifier.userMessage(kind, message),
                ),
                ConnectionEffect.CloseTunnel,
            )
        }

        // Encerramento já solicitado: não reage a falha da própria desconexão.
        if (state.status == TunnelStatus.Stopping) {
            return commit(state, ConnectionEffect.None)
        }

        if (!ConnectionErrorClassifier.isRetryable(kind)) {
            return commit(
                state.copy(
                    status = TunnelStatus.Error,
                    nextRetryInMs = null,
                    error = ConnectionErrorClassifier.userMessage(kind, message),
                ),
                ConnectionEffect.CloseTunnel,
            )
        }

        return scheduleRetry(message)
    }

    private fun onTunnelLost(message: String?): Transition {
        // Desconexão intencional não deve virar reconexão.
        if (state.status == TunnelStatus.Stopping || state.profileId == null) {
            return commit(state, ConnectionEffect.None)
        }
        return scheduleRetry(message)
    }

    private fun scheduleRetry(message: String?): Transition {
        val nextAttempt = state.attempt + 1
        val delay = policy.delayFor(nextAttempt)
        return commit(
            state.copy(
                status = TunnelStatus.Reconnecting,
                attempt = nextAttempt,
                nextRetryInMs = delay,
                error = message,
                localPort = null,
            ),
            ConnectionEffect.ScheduleRetry(delay, nextAttempt),
        )
    }

    private fun onRetryDue(): Transition {
        if (state.status != TunnelStatus.Reconnecting) {
            return commit(state, ConnectionEffect.None)
        }
        return commit(
            state.copy(status = TunnelStatus.Connecting, nextRetryInMs = null),
            ConnectionEffect.OpenTunnel,
        )
    }

    private fun onDisconnect(): Transition = commit(
        ConnectionSnapshot(
            status = TunnelStatus.Stopping,
            profileId = state.profileId,
        ),
        ConnectionEffect.CloseTunnel,
    )

    private fun onCleanupDone(): Transition = commit(
        ConnectionSnapshot(status = TunnelStatus.Disconnected),
        ConnectionEffect.StopService,
    )

    private fun commit(
        newState: ConnectionSnapshot,
        effect: ConnectionEffect,
    ): Transition {
        state = newState
        return Transition(newState, effect)
    }
}
