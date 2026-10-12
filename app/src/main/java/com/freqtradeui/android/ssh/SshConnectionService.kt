package com.freqtradeui.android.ssh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.freqtradeui.android.FreqtradeApplication
import com.freqtradeui.android.MainActivity
import com.freqtradeui.android.R
import com.freqtradeui.android.data.models.ConnectionProfile
import com.freqtradeui.android.data.models.ConnectionSnapshot
import com.freqtradeui.android.data.models.TunnelStatus
import com.freqtradeui.android.security.HostKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service que mantém o túnel SSH vivo independentemente da UI.
 *
 * Por que um serviço em primeiro plano: o túnel precisa sobreviver à Activity
 * ser destruída (rotação de tela, app removido dos recentes, usuário em outra
 * tela). Manter a conexão em um ViewModel a encerraria junto com a Activity —
 * exatamente o que não pode acontecer.
 *
 * **Tipo de serviço:** `dataSync` — o tipo correto para transferência contínua
 * de dados em nome do usuário, e não `mediaPlayback`, `location` ou
 * `connectedDevice`. Nenhum tipo é declarado apenas para driblar restrição.
 *
 * **Reinício:** `START_NOT_STICKY` é deliberado. Se o processo for morto por
 * pressão de memória, o Android reentregaria a intenção e o serviço tentaria
 * conectar sem ação do usuário, sem a credencial em memória e sem
 * consentimento. A UI recupera o estado ao reabrir e o usuário retoma a
 * conexão. Intencional e documentado no README.
 */
class SshConnectionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Perfil em curso, usado na notificação e ao reconectar. */
    private var activeProfile: ConnectionProfile? = null

    /** Evita coletar o estado mais de uma vez por instância. */
    private var observing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Obrigatório antes de qualquer operação longa a partir do Android 12.
        startForegroundCompat(
            title = getString(R.string.ssh_notification_title),
            text = getString(R.string.ssh_state_connecting),
        )

        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnelAndSelf()
                return START_NOT_STICKY
            }
        }

        val profileId = intent?.getStringExtra(EXTRA_PROFILE_ID)
        if (profileId == null) {
            stopTunnelAndSelf()
            return START_NOT_STICKY
        }

        if (!observing) {
            observing = true
            serviceScope.launch {
                var lastConnected = false
                var sawSession = false
                tunnelManager.state.collect { snapshot ->
                    _snapshot.value = snapshot
                    updateNotification(snapshot)
                    // Registra a última conexão bem sucedida só na transição,
                    // para não gravar em cada emissão de estado.
                    val connected = snapshot.status == TunnelStatus.Connected
                    if (connected && !lastConnected) {
                        activeProfile?.let { profile ->
                            (application as FreqtradeApplication)
                                .container
                                .profileRepository
                                .markConnected(profile.id, System.currentTimeMillis())
                        }
                    }
                    lastConnected = connected

                    // O serviço vive enquanto há túnel, tentativa ou decisão
                    // TOFU pendente; ao chegar a um Disconnected limpo após
                    // ter estado ativo, encerra a si próprio e remove a
                    // notificação — sem deixar "Encerrando/Desconectado"
                    // pendurado para sempre.
                    if (snapshot.status != TunnelStatus.Disconnected ||
                        snapshot.hasPendingHostKey
                    ) {
                        sawSession = true
                    }
                    if (sawSession &&
                        snapshot.status == TunnelStatus.Disconnected &&
                        !snapshot.hasPendingHostKey
                    ) {
                        ServiceCompat.stopForeground(
                            this@SshConnectionService,
                            ServiceCompat.STOP_FOREGROUND_REMOVE,
                        )
                        stopSelf()
                    }
                }
            }
        }

        // Carrega o perfil e a credencial em I/O; connect() é bloqueante e não
        // pode rodar na thread principal.
        serviceScope.launch(Dispatchers.IO) {
            val repository = (application as FreqtradeApplication).container.profileRepository
            val profile = repository.profile(profileId)
            val secret = profile?.let { repository.secret(it.id) }
            if (profile == null || secret == null) {
                // Sem perfil ou sem chave gravada: nada a manter.
                stopTunnelAndSelf()
                return@launch
            }
            activeProfile = profile
            tunnelManager.connect(profile, secret)
        }

        return START_NOT_STICKY
    }

    /** Encerra a conexão e o serviço, sem deixar sessão órfã. */
    private fun stopTunnelAndSelf() {
        serviceScope.launch(Dispatchers.IO) {
            tunnelManager.disconnect()
        }
        ServiceCompat.stopForeground(
            this,
            ServiceCompat.STOP_FOREGROUND_REMOVE,
        )
        stopSelf()
    }

    override fun onDestroy() {
        // Cancela antes de liberar recursos: impede que o loop de reconexão
        // abra uma nova sessão enquanto o serviço está sendo destruído.
        serviceScope.cancel()
        tunnelManager.shutdown()
        activeProfile = null
        super.onDestroy()
    }

    private fun startForegroundCompat(title: String, text: String) {
        val notification = buildNotification(title, text, ongoing = true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    /**
     * Atualiza a notificação com o estado **real** do túnel. A UI e a
     * notificação leem a mesma fonte, então não podem divergir.
     */
    private fun updateNotification(snapshot: ConnectionSnapshot) {
        val profile = activeProfile
        val title = if (profile != null) {
            getString(R.string.ssh_notification_profile, profile.name)
        } else {
            getString(R.string.ssh_notification_title)
        }

        val text = when (snapshot.status) {
            TunnelStatus.Connecting -> getString(R.string.ssh_state_connecting)
            TunnelStatus.Connected -> if (snapshot.localPort != null) {
                getString(R.string.ssh_state_connected_tunnel, snapshot.localPort)
            } else {
                getString(R.string.ssh_state_connected)
            }
            TunnelStatus.Reconnecting -> {
                val seconds = ((snapshot.nextRetryInMs ?: 0L) / 1000L).toInt()
                getString(R.string.ssh_state_reconnecting, seconds)
            }
            TunnelStatus.Stopping -> getString(R.string.ssh_state_stopping)
            TunnelStatus.Error ->
                snapshot.error ?: getString(R.string.ssh_state_error)
            TunnelStatus.Disconnected -> getString(R.string.ssh_state_disconnected)
        }

        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIFICATION_ID, buildNotification(title, text, ongoing = true))
    }

    /**
     * Monta a notificação persistente.
     *
     * A visibilidade é [NotificationCompat.VISIBILITY_SECRET]: o conteúdo não
     * aparece em tela de bloqueio, evitando expor qual servidor o usuário acessa.
     */
    private fun buildNotification(
        title: String,
        text: String,
        ongoing: Boolean,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val disconnectIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SshConnectionService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSmallIcon(R.drawable.ic_stat_tunnel)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(0, getString(R.string.ssh_action_disconnect), disconnectIntent)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.ssh_channel_name),
            // LOW: muda com frequência durante reconexões e não deve alertar a
            // cada troca de estado.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.ssh_channel_description)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ssh_tunnel"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_PROFILE_ID = "profile_id"
        private const val ACTION_DISCONNECT = "com.freqtradeui.android.action.DISCONNECT"

        /** Último estado conhecido, para a UI exibir algo ao abrir. */
        private val _snapshot = MutableStateFlow(ConnectionSnapshot())
        val snapshot: StateFlow<ConnectionSnapshot> = _snapshot.asStateFlow()

        /** Instância compartilhada do gerenciador de túnel. */
        val tunnelManager: SshTunnelManager by lazy {
            SshTunnelManager(hostKeyStore)
        }

        /** Instância compartilhada do repositório de chaves de host. */
        val hostKeyStore: HostKeyStore by lazy {
            HostKeyStore(FreqtradeApplication.appContext)
        }

        /**
         * Inicia o túnel para [profileId]. Só após ação explícita do usuário.
         */
        fun start(context: Context, profileId: String) {
            val intent = Intent(context, SshConnectionService::class.java)
                .putExtra(EXTRA_PROFILE_ID, profileId)
            context.startForegroundService(intent)
        }

        /** Solicita desconexão; o serviço se encerra sozinho. */
        fun stop(context: Context) {
            val intent = Intent(context, SshConnectionService::class.java)
                .setAction(ACTION_DISCONNECT)
            context.startService(intent)
        }
    }
}

