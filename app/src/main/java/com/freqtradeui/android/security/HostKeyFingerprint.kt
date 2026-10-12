package com.freqtradeui.android.security

import java.security.MessageDigest
import java.util.Base64

/**
 * Utilitários para identificação de chaves de host SSH.
 *
 * Trabalha sobre os bytes brutos do blob da chave pública (formato de
 * transmissão SSH), exatamente como recebido em
 * `HostKeyRepository.check(host, key)`. É código puro, sem dependência de
 * Android, o que permite teste unitário direto.
 */
object HostKeyFingerprint {

    /**
     * Impressão digital no formato `SHA256:<base64>`, equivalente ao exibido
     * por `ssh-keygen -lf`. É o valor que o usuário deve comparar fora de banda
     * antes de confiar em uma chave de host desconhecida.
     */
    fun sha256(key: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key)
        val encoded = Base64.getEncoder().withoutPadding().encodeToString(digest)
        return "SHA256:$encoded"
    }

    /**
     * Extrai o algoritmo da chave (ex.: `ssh-ed25519`, `ecdsa-sha2-nistp256`,
     * `rsa-sha2-512`) do início do blob SSH.
     *
     * O blob começa com uma string de 4 bytes (big-endian) contendo o nome do
     * algoritmo. Retorna `"desconhecido"` se o blob estiver truncado.
     */
    fun algorithm(key: ByteArray): String {
        if (key.size < 4) return "desconhecido"
        val length = ((key[0].toInt() and 0xFF) shl 24) or
            ((key[1].toInt() and 0xFF) shl 16) or
            ((key[2].toInt() and 0xFF) shl 8) or
            (key[3].toInt() and 0xFF)
        // Sanidade: nome de algoritmo curto e dentro dos limites do blob.
        if (length <= 0 || length > 64 || 4 + length > key.size) return "desconhecido"
        return String(key, 4, length, Charsets.US_ASCII)
    }

    /**
     * Compara dois blobs de chave em tempo constante, evitando vazamento de
     * informação por análise de tempo de comparação.
     */
    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)
}
