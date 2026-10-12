package com.freqtradeui.android.ssh

import com.freqtradeui.android.data.models.ConnectionState

/**
 * Estado do túnel SSH local, consumido pela interface.
 *
 * @param ssh estado do túnel (espelha [ConnectionState]).
 * @param localPort porta local encaminhada, quando conectado.
 * @param pendingHostKey chave de host ainda não reconhecida aguardando
 *        decisão do usuário (TOFU). Nula quando não há decisão pendente.
 * @param message mensagem de status/erro amigável para exibição.
 */
data class TunnelState(
    val ssh: ConnectionState = ConnectionState.Disconnected,
    val localPort: Int? = null,
    val pendingHostKey: PendingHostKey? = null,
    val message: String? = null,
)

/**
 * Chave de host apresentada ao usuário para decisão de confiança (TOFU).
 *
 * @param host host da conexão.
 * @param fingerprint impressão digital curta (hash) da chave pública.
 * @param keyType tipo/algoritmo da chave (ex.: "ssh-ed25519").
 * @param keyBytes chave pública codificada, usada para confirmar a decisão
 *        sem refazer o handshake.
 */
data class PendingHostKey(
    val host: String,
    val fingerprint: String,
    val keyType: String,
    val keyBytes: ByteArray,
) {
    // data class com ByteArray exige equals/hashCode explícitos.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PendingHostKey) return false
        return host == other.host &&
            fingerprint == other.fingerprint &&
            keyType == other.keyType &&
            keyBytes.contentEquals(other.keyBytes)
    }

    override fun hashCode(): Int {
        var result = host.hashCode()
        result = 31 * result + fingerprint.hashCode()
        result = 31 * result + keyType.hashCode()
        result = 31 * result + keyBytes.contentHashCode()
        return result
    }
}
