package com.example.service

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val FGS_TAG = "RobotBackgroundService"

/**
 * Foreground service keeping the Trad do futuro Roberto Silva AI analyzer and auto-trade robot
 * alive in the background even after the user leaves the app, until the user explicitly pauses it
 * either inside the app or from the persistent notification's "Pausar IA" action.
 */
class RobotBackgroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_PAUSE_ALL) {
            Log.d(FGS_TAG, "User tapped Pausar IA in foreground notification")
            _pauseRequests.tryEmit(Unit)
            isRunning = false
            runCatching {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val title = intent?.getStringExtra(EXTRA_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.bg_notif_default_title)
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE)
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.bg_notif_default_subtitle)

        val notification = buildNotification(title = title, subtitle = subtitle)
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        }.onFailure { err ->
            Log.e(FGS_TAG, "Unable to enter foreground service mode", err)
        }.getOrDefault(false)

        if (started) {
            isRunning = true
            return START_STICKY
        }
        isRunning = false
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(FGS_TAG, "Foreground service timeout reached for type=$fgsType; stopping cleanly")
        isRunning = false
        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.bg_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.bg_notif_channel_desc)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, subtitle: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            REQ_OPEN_APP,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, RobotBackgroundService::class.java).apply {
            action = ACTION_PAUSE_ALL
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            REQ_PAUSE_ALL,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_robot_active)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setStyle(NotificationCompat.BigTextStyle().bigText(subtitle))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                getString(R.string.bg_notif_action_pause),
                pausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_view,
                getString(R.string.bg_notif_action_open),
                openAppPendingIntent
            )
            .build()
    }

    companion object {
        const val CHANNEL_ID = "robot_background_monitoring"
        const val NOTIFICATION_ID = 7088
        const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE_ROBOT_BG"
        const val ACTION_PAUSE_ALL = "com.example.action.PAUSE_ROBOT_BG"
        private const val EXTRA_TITLE = "extra_bg_title"
        private const val EXTRA_SUBTITLE = "extra_bg_subtitle"
        private const val REQ_OPEN_APP = 101
        private const val REQ_PAUSE_ALL = 102

        @Volatile
        var isRunning: Boolean = false
            private set

        private val _pauseRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
        val pauseRequests: SharedFlow<Unit> = _pauseRequests.asSharedFlow()

        fun startOrUpdate(
            context: Context,
            title: String,
            subtitle: String
        ) {
            val appCtx = context.applicationContext
            val intent = Intent(appCtx, RobotBackgroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
            }
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }.onFailure { err ->
                Log.w(FGS_TAG, "Could not start/update RobotBackgroundService: ${err.message}")
            }
        }

        fun stop(context: Context) {
            val appCtx = context.applicationContext
            isRunning = false
            runCatching {
                appCtx.stopService(Intent(appCtx, RobotBackgroundService::class.java))
            }
        }
    }
}
