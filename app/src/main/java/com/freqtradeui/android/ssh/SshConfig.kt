package com.freqtradeui.android.ssh

/**
 * Configuração de conexão SSH por chave privada.
 *
 * Este modelo **não** armazena a chave em texto puro em memória por longos
 * períodos: quando persistido, é gravado pelo [com.freqtradeui.android.security.SecureCredentialStore]
 * via EncryptedSharedPreferences. A senha da chave é opcional (chaves sem
 * passphrase).
 *
 * @param host hostname ou IP da VPS.
 * @param sshPort porta SSH do servidor (padrão 22).
 * @param username usuário SSH de autenticação.
 * @param privateKey conteúdo da chave privada no formato OpenSSH ou PEM.
 * @param privateKeyPassphrase senha da chave privada, se houver.
 * @param remoteHost host remoto acessível a partir da VPS (loopback do Freqtrade).
 * @param remotePort porta do Freqtrade na VPS.
 * @param localPort porta local usada pelo encaminhamento no dispositivo.
 * @param privateKeyFileName nome do arquivo de origem da chave. É apenas
 *        metadado de interface (não é segredo) e não participa da conexão;
 *        permite reapresentar a seleção ao reabrir as configurações sem exibir
 *        o conteúdo da chave.
 */
data class SshConfig(
    val host: String,
    val sshPort: Int = DEFAULT_SSH_PORT,
    val username: String,
    val privateKey: String,
    val privateKeyPassphrase: String? = null,
    val remoteHost: String = "127.0.0.1",
    val remotePort: Int = DEFAULT_REMOTE_PORT,
    val localPort: Int = DEFAULT_LOCAL_PORT,
    val privateKeyFileName: String? = null,
) {
    init {
        require(host.isNotBlank()) { "O host SSH não pode ser vazio." }
        require(username.isNotBlank()) { "O usuário SSH não pode ser vazio." }
        require(privateKey.isNotBlank()) { "A chave privada não pode ser vazia." }
        require(sshPort in 1..65535) { "Porta SSH inválida: $sshPort" }
        require(remotePort in 1..65535) { "Porta remota inválida: $remotePort" }
        require(localPort in 1024..65535) { "Porta local inválida: $localPort" }
    }

    companion object {
        const val DEFAULT_SSH_PORT = 22
        const val DEFAULT_REMOTE_PORT = 8080
        const val DEFAULT_LOCAL_PORT = 18080
    }
}
