package com.freqtradeui.android

import android.app.Application
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.freqtradeui.android.data.repository.ConnectionProfileRepository
import com.freqtradeui.android.data.repository.DataStoreProfileStorage
import com.freqtradeui.android.security.HostKeyStore
import com.freqtradeui.android.security.SecureSecretStore
import com.freqtradeui.android.ssh.SshConnectionService
import com.freqtradeui.android.ssh.SshTunnelManager
import com.freqtradeui.android.ui.dashboard.DashboardViewModel
import com.freqtradeui.android.ui.profiles.ProfilesViewModel

/**
 * Ponto de composição das dependências de longa duração.
 *
 * Agrupa em [container] o que é compartilhado entre UI e serviço: o
 * gerenciador do túnel, os repositórios e o cofre de segredos. Composição
 * manual e explícita, sem framework de injeção.
 */
class FreqtradeApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext
        instance = this
    }

    /**
     * Dependências compartilhadas. O gerenciador do túnel é uma instância só,
     * usada tanto pela UI quanto pelo serviço — assim o estado exibido é o
     * mesmo que o serviço mantém.
     */
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    /** Fábrica de ViewModels das telas. */
    val viewModelFactory: ViewModelProvider.Factory by lazy {
        viewModelFactory {
            initializer {
                ProfilesViewModel(container.profileRepository, container.tunnelManager)
            }
            initializer {
                DashboardViewModel(container.profileRepository, container.tunnelManager)
            }
        }
    }

    override fun onTerminate() {
        // Não roda em dispositivos reais, mas limpa em testes e emuladores.
        container.tunnelManager.shutdown()
        super.onTerminate()
    }

    companion object {
        /** Contexto de aplicação, usado pelo serviço para criar o túnel. */
        lateinit var appContext: Context
            private set

        /** Instância em execução, ou `null` se o processo ainda não subiu. */
        var instance: FreqtradeApplication? = null
            private set
    }
}

/**
 * Agrupamento das dependências compartilhadas.
 *
 * @param tunnelManager gerenciador do túnel SSH (instância única).
 * @param profileRepository acesso a perfis e segredos.
 */
class AppContainer(context: Context) {
    val tunnelManager: SshTunnelManager = SshConnectionService.tunnelManager

    val profileRepository: ConnectionProfileRepository = ConnectionProfileRepository(
        storage = DataStoreProfileStorage(context),
        secrets = SecureSecretStore(context),
    )

    /**
     * Chaves de host confiáveis. A mesma instância do gerenciador de túnel,
     * para não abrir duas vezes o arquivo cifrado.
     */
    val hostKeyStore: HostKeyStore = SshConnectionService.hostKeyStore
}

