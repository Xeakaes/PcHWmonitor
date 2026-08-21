package com.Obscrum.pchwmonitor.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.Obscrum.pchwmonitor.MainActivity
import com.Obscrum.pchwmonitor.R
import com.Obscrum.pchwmonitor.data.network.ConnectionState
import com.Obscrum.pchwmonitor.domain.model.SystemStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MonitorNotificationService : Service() {

    companion object {
        const val CHANNEL_ID = "pchw_monitor_metrics"
        const val NOTIFICATION_ID = 1001

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        private val _currentStatus = MutableStateFlow<SystemStatus?>(null)
        private var _instance: MonitorNotificationService? = null

        fun start(context: Context) {
            val intent = Intent(context, MonitorNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorNotificationService::class.java))
        }

        fun updateConnectionState(state: ConnectionState) {
            _connectionState.value = state
            _instance?.updateNotification()
        }

        fun updateStatus(status: SystemStatus?) {
            _currentStatus.value = status
            _instance?.updateNotification()
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        _instance = this
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        _instance = null
        scope.cancel()
        _isRunning.value = false
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val status = _currentStatus.value
        val connection = _connectionState.value

        val contentText = when (connection) {
            ConnectionState.CONNECTED -> {
                if (status != null) buildMetricsText(status)
                else getString(R.string.notification_waiting_data)
            }
            ConnectionState.CONNECTING -> getString(R.string.notification_connecting)
            ConnectionState.DISCONNECTED -> getString(R.string.notification_disconnected)
        }

        val expandedText = if (status != null && connection == ConnectionState.CONNECTED) {
            buildExpandedText(status)
        } else {
            contentText
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun buildMetricsText(status: SystemStatus): String {
        val parts = mutableListOf<String>()
        status.cpu?.let { cpu ->
            cpu.usagePct?.let { parts.add("CPU: %.0f%%".format(it)) }
            cpu.tempC?.let { parts.add("CPU: %.0f°C".format(it)) }
        }
        status.gpu?.let { gpu ->
            gpu.tempC?.let { parts.add("GPU: %.0f°C".format(it)) }
            gpu.hotspotC?.let { parts.add("GPU Hot: %.0f°C".format(it)) }
        }
        status.ram?.let { ram ->
            ram.usagePct?.let { parts.add("RAM: %.0f%%".format(it)) }
        }
        status.fps?.let { fps ->
            fps.current?.let { parts.add("FPS: %.0f".format(it)) }
        }
        return parts.joinToString(" · ").ifEmpty { getString(R.string.notification_waiting_data) }
    }

    private fun buildExpandedText(status: SystemStatus): String {
        val lines = mutableListOf<String>()
        status.cpu?.let { cpu ->
            lines.add("── CPU ──")
            cpu.usagePct?.let { lines.add("  Kullanım: %.0f%%".format(it)) }
            cpu.tempC?.let { lines.add("  Sıcaklık: %.0f°C".format(it)) }
            cpu.clockMhz?.let { lines.add("  Frekans: %.0f MHz".format(it)) }
            cpu.powerW?.let { lines.add("  Güç: %.1f W".format(it)) }
        }
        status.gpu?.let { gpu ->
            lines.add("── GPU ──")
            gpu.tempC?.let { lines.add("  Sıcaklık: %.0f°C".format(it)) }
            gpu.hotspotC?.let { lines.add("  Hotspot: %.0f°C".format(it)) }
            gpu.usagePct?.let { lines.add("  Kullanım: %.0f%%".format(it)) }
            gpu.coreClockMhz?.let { lines.add("  Frekans: %.0f MHz".format(it)) }
            gpu.powerW?.let { lines.add("  Güç: %.1f W".format(it)) }
        }
        status.ram?.let { ram ->
            lines.add("── RAM ──")
            ram.usagePct?.let { lines.add("  Kullanım: %.0f%%".format(it)) }
            ram.usedGb?.let { lines.add("  Kullanılan: %.1f GB".format(it)) }
        }
        status.fps?.let { fps ->
            lines.add("── FPS ──")
            fps.current?.let { lines.add("  FPS: %.0f".format(it)) }
            fps.onePercentLow?.let { lines.add("  1%% Low: %.0f".format(it)) }
        }
        return lines.joinToString("\n").ifEmpty { getString(R.string.notification_waiting_data) }
    }

    private fun updateNotification() {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification())
    }
}
