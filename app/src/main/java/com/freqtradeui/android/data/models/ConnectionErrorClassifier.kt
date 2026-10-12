package com.freqtradeui.android.data.models

/**
 * Classifica falhas de conexão SSH em transitórias ou permanentes.
 *
 * Puro e sem dependência do JSch: recebe a mensagem de erro como texto, o que
 * também o torna diretamente testável.
 */
object ConnectionErrorClassifier {

    /**
     * Determina a natureza da falha a partir da mensagem da exceção.
     *
     * @param message mensagem do erro (pode ser `null`).
     * @return a classificação correspondente.
     */
    fun classify(message: String?): FailureKind {
        val text = message?.lowercase() ?: return FailureKind.Transient

        // A ordem importa: "HostKey" aparece tanto em desconhecida quanto em
        // alterada, e a segunda é a hipótese mais grave — deve ser testada
        // antes para não ser mascarada pelo caso genérico.
        if (text.contains("hostkey has changed") ||
            text.contains("host key has changed") ||
            text.contains("changedhostkey")
        ) {
            return FailureKind.ChangedHostKey
        }

        if (text.contains("unknownhostkey") ||
            text.contains("unknown host key") ||
            text.contains("hostkey: unknown")
        ) {
            return FailureKind.UnknownHostKey
        }

        // Falhas de autenticação nunca se resolvem repetindo a tentativa.
        if (text.contains("auth fail") ||
            text.contains("auth cancel") ||
            text.contains("userauth") ||
            text.contains("invalid privatekey") ||
            text.contains("invalid private key") ||
            text.contains("passphrase") ||
            text.contains("permission denied")
        ) {
            return FailureKind.Authentication
        }

        // Porta local já em uso: problema de configuração do app, não da rede.
        if (text.contains("portforwardingl") ||
            text.contains("already in use") ||
            text.contains("address already in use") ||
            text.contains("cannot bind") ||
            text.contains("local port")
        ) {
            return FailureKind.Configuration
        }

        // Perda de rede, timeout, socket fechado, DNS: vale tentar de novo.
        if (text.contains("timeout") ||
            text.contains("timed out") ||
            text.contains("socket") ||
            text.contains("connection refused") ||
            text.contains("connection reset") ||
            text.contains("network is unreachable") ||
            text.contains("no route to host") ||
            text.contains("session is down") ||
            text.contains("channel is not opened") ||
            text.contains("eof") ||
            text.contains("closed")
        ) {
            return FailureKind.Transient
        }

        // Sem reconhecimento, assume-se transitório: é melhor uma nova
        // tentativa (barrada pela política de backoff) do que deixar o usuário
        // sem túnel por causa de uma mensagem inesperada.
        return FailureKind.Transient
    }

    /** Indica se a falha justifica uma nova tentativa automática. */
    fun isRetryable(kind: FailureKind): Boolean = kind == FailureKind.Transient

    /**
     * Mensagem amigável para a classificação, sem expor detalhes internos.
     *
     * @param kind classificação da falha.
     * @param detail mensagem original, opcional, para casos transitórios.
     */
    fun userMessage(kind: FailureKind, detail: String? = null): String = when (kind) {
        FailureKind.Authentication ->
            "Falha de autenticação. Verifique a chave privada e a frase secreta."
        FailureKind.UnknownHostKey ->
            "Chave de host desconhecida. Confira a impressão digital antes de confiar."
        FailureKind.ChangedHostKey ->
            "A chave de host mudou desde a última conexão. Por segurança, a conexão " +
                "foi bloqueada. Confirme se o servidor foi recriado antes de aprovar."
        FailureKind.Configuration ->
            detail?.takeIf { it.isNotBlank() }
                ?: "Configuração inválida. Revise o endereço e a porta local."
        FailureKind.Transient ->
            detail?.takeIf { it.isNotBlank() }
                ?: "Falha de rede. Tentando reconectar."
    }
}
