package com.freqtradeui.android.data.models

import java.util.UUID

/**
 * Perfil de conexão SSH persistido no dispositivo.
 *
 * Contém **apenas dados não sensíveis**. A chave privada e a frase secreta
 * nunca ficam aqui: são guardadas separadamente pelo
 * [com.freqtradeui.android.security.SecureSecretStore], cifradas, indexadas por
 * [id]. Assim um backup ou despejo do armazenamento de perfis não vaza segredos.
 *
 * O identificador é um UUID gerado pelo aplicativo: dois perfis podem ter o
 * mesmo nome amigável, e renomear não pode afetar as credenciais nem as chaves
 * de host já aprovadas.
 *
 * @param id identificador único e estável do perfil.
 * @param name nome amigável escolhido pelo usuário.
 * @param host endereço IP ou hostname da VPS.
 * @param sshPort porta SSH do servidor.
 * @param username usuário SSH.
 * @param remoteHost host acessível a partir da VPS (loopback do Freqtrade).
 * @param remotePort porta do Freqtrade na VPS.
 * @param localPort porta local usada pelo encaminhamento no dispositivo.
 * @param privateKeyFileName nome do arquivo de origem da chave; apenas
 *        metadado de interface, não é segredo.
 * @param hostKeyFingerprint impressão digital SHA-256 aprovada pelo usuário,
 *        quando já houver uma decisão TOFU registrada para este perfil.
 * @param lastConnectedAt instante (epoch millis) da última conexão bem
 *        sucedida, ou `null` se o perfil nunca conectou neste dispositivo.
 */
data class ConnectionProfile(
    val id: String,
    val name: String,
    val host: String,
    val sshPort: Int = DEFAULT_SSH_PORT,
    val username: String,
    val remoteHost: String = DEFAULT_REMOTE_HOST,
    val remotePort: Int = DEFAULT_REMOTE_PORT,
    val localPort: Int = DEFAULT_LOCAL_PORT,
    val privateKeyFileName: String? = null,
    val hostKeyFingerprint: String? = null,
    val lastConnectedAt: Long? = null,
) {

    /** Rótulo curto do destino, exibido sob o nome do perfil na lista. */
    val destination: String get() = "$username@$host:$sshPort"

    /**
     * Primeiro problema de validação encontrado, ou `null` se o perfil estiver
     * consistente. As mensagens são voltadas ao usuário final.
     */
    fun validationError(): String? = when {
        name.isBlank() -> "Informe um nome para o perfil."
        host.isBlank() -> "Informe o endereço da VPS."
        username.isBlank() -> "Informe o usuário SSH."
        sshPort !in 1..65535 -> "Porta SSH inválida: $sshPort."
        remoteHost.isBlank() -> "Informe o host remoto do encaminhamento."
        remotePort !in 1..65535 -> "Porta remota inválida: $remotePort."
        // Portas abaixo de 1024 exigem privilégios de root no Linux e não podem
        // ser abertas por um app Android comum.
        localPort !in 1024..65535 -> "A porta local deve estar entre 1024 e 65535."
        else -> null
    }

    /** Indica se o perfil pode ser usado para conectar. */
    val isValid: Boolean get() = validationError() == null

    companion object {
        const val DEFAULT_SSH_PORT = 22
        const val DEFAULT_REMOTE_PORT = 8080
        const val DEFAULT_REMOTE_HOST = "127.0.0.1"
        const val DEFAULT_LOCAL_PORT = 18080

        /** Cria um identificador único para um novo perfil. */
        fun newId(): String = UUID.randomUUID().toString()
    }
}

/**
 * Segredos de um perfil, mantidos fora do modelo persistido.
 *
 * @param privateKey conteúdo da chave privada OpenSSH/PEM.
 * @param passphrase frase secreta da chave, quando houver.
 */
data class ProfileSecret(
    val privateKey: String,
    val passphrase: String? = null,
)
