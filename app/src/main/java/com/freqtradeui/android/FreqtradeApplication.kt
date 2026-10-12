package com.freqtradeui.android

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.freqtradeui.android.security.HostKeyStore
import com.freqtradeui.android.security.SecureCredentialStore
import com.freqtradeui.android.ssh.SshTunnelManager
import com.freqtradeui.android.ui.dashboard.DashboardViewModel
import com.freqtradeui.android.ui.settings.SettingsViewModel

/**
 * Ponto de composição das dependências de longa duração.
 *
 * Mantém instâncias únicas do armazenamento seguro de credenciais, do
 * repositório de chaves de host e do gerenciador do túnel SSH. Esses objetos
 * sobrevivem a mudanças de configuração da Activity, preservando o estado do
 * túnel entre telas. Sem frameworks de injeção: composição manual e explícita.
 */
class FreqtradeApplication : Application() {

    /** Armazenamento criptografado de credenciais SSH (EncryptedSharedPreferences). */
    val credentialStore: SecureCredentialStore by lazy { SecureCredentialStore(this) }

    /** Repositório de chaves de host confiáveis (TOFU). */
    val hostKeyStore: HostKeyStore by lazy { HostKeyStore(this) }

    /** Gerenciador do túnel SSH local, compartilhado por toda a UI. */
    val sshTunnelManager: SshTunnelManager by lazy { SshTunnelManager(hostKeyStore) }

    /**
     * Fábrica de ViewModels usada pelas telas. Centraliza a criação para que
     * Dashboard e Configurações compartilhem o mesmo gerenciador de túnel e o
     * mesmo armazenamento de credenciais.
     */
    val viewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory {
            initializer { DashboardViewModel(sshTunnelManager, credentialStore) }
            initializer { SettingsViewModel(credentialStore, sshTunnelManager) }
        }
    }

    override fun onTerminate() {
        // onTerminate não roda em dispositivos reais, mas garante limpeza em
        // cenários de teste/emulador.
        sshTunnelManager.shutdown()
        super.onTerminate()
    }
}
