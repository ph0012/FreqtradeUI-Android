package com.freqtradeui.android.data.models

/**
 * Estados explícitos da máquina de conexão SSH.
 *
 * A UI reflete exatamente este estado — nunca uma variável local otimista. Em
 * particular, reabrir o app não pode mostrar `Connected` só porque o perfil
 * estava conectado antes: o estado real vem do serviço.
 */
enum class TunnelStatus {
    /** Sem túnel; nada em andamento. */
    Disconnected,

    /** Primeira tentativa de conexão, solicitada pelo usuário. */
    Connecting,

    /** Túnel e encaminhamento de porta ativos. */
    Connected,

    /** O túnel caiu ou falhou de forma transitória; nova tentativa agendada. */
    Reconnecting,

    /** Falha que exige ação do usuário (credencial, host key, configuração). */
    Error,

    /** Encerramento solicitado pelo usuário, liberando recursos. */
    Stopping,

    ;

    /** Indica se uma sessão SSH está ativa. */
    val isActive: Boolean get() = this == Connected || this == Reconnecting
}

/**
 * Snapshot observável do estado de conexão, publicado pelo serviço.
 *
 * @param status estado da máquina.
 * @param profileId perfil ao qual o estado se refere (`null` quando não há
 *        nenhuma conexão em curso).
 * @param localPort porta local encaminhada, quando conectado.
 * @param attempt número da tentativa atual (0 = primeira).
 * @param nextRetryInMs espera calculada até a próxima tentativa, quando
 *        [TunnelStatus.Reconnecting]. Informativo para a UI exibir uma
 *        contagem regressiva aproximada.
 * @param pendingHostKey chave de host aguardando decisão TOFU do usuário.
 * @param error mensagem de erro amigável; presente em [TunnelStatus.Error].
 */
data class ConnectionSnapshot(
    val status: TunnelStatus = TunnelStatus.Disconnected,
    val profileId: String? = null,
    val localPort: Int? = null,
    val attempt: Int = 0,
    val nextRetryInMs: Long? = null,
    val pendingHostKey: String? = null,
    val error: String? = null,
) {
    /** Indica se o snapshot se refere ao perfil informado. */
    fun refersTo(profileId: String?): Boolean =
        profileId != null && this.profileId == profileId

    /** `true` quando uma decisão de chave de host está pendente. */
    val hasPendingHostKey: Boolean get() = pendingHostKey != null

    /** `true` quando a falha impede nova tentativa automática. */
    val isTerminalError: Boolean get() = status == TunnelStatus.Error
}

/**
 * Classificação de falhas de conexão.
 *
 * Distinguir transitório de permanente é o que evita loops inúteis: perder o
 * Wi-Fi justifica reconectar, mas senha ou chave erradas jamais vão funcionar
 * repetindo a mesma tentativa.
 */
enum class FailureKind {
    /** Perda de rede, timeout, queda de sessão: vale tentar de novo. */
    Transient,

    /** Falha de autenticação: não adianta repetir com a mesma chave. */
    Authentication,

    /** Chave de host desconhecida: depende de decisão do usuário. */
    UnknownHostKey,

    /** Chave de host diferente da aprovada: possível ataque. Nunca aprovar automaticamente. */
    ChangedHostKey,

    /** Configuração inválida (porta local ocupada, host vazio, etc). */
    Configuration,
}
