package com.tomtom.demo.nav.feature.guidance

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
import com.tomtom.demo.nav.App
import com.tomtom.demo.nav.R
import com.tomtom.demo.nav.feature.home.MainActivity
import com.tomtom.demo.nav.feature.widget.NavWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ［feature:guidance · WS5］导航前台 Service：
 * - 满足"回主页导航后台运行"需求，防止进程被系统回收；
 * - GuidanceBus 消费侧宿主：通知栏 / 桌面 Widget / 仪表通道（ClusterChannel）/ ISA 转发都在此驱动
 *   —— 对应 Workshop《多屏引导数据流》页的消费端扇出。
 */
class NavigationForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        observeGuidance()
        return START_STICKY
    }

    private fun startInForeground() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.nav_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.nav_running)), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.nav_running)))
        }
    }

    private fun observeGuidance() {
        val container = (application as App).container
        // ISA 限速转发：限速数据流在导航期间持续下发整车（core/sdk/guidance → core/platform VehicleBus）
        container.isaForwarder.start(scope)
        scope.launch {
            container.guidanceBus.snapshot.collect { snapshot ->
                if (!snapshot.navigating) return@collect
                val text = buildString {
                    snapshot.nextInstruction?.let { append(it) }
                    snapshot.distanceToNextMeters?.let { append("（${it.toInt()}m)") }
                    if (isEmpty()) append(getString(R.string.nav_running))
                }
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, buildNotification(text))
                NavWidgetProvider.update(this@NavigationForegroundService, text, snapshot.remainingTimeSeconds)
                container.clusterChannel.publishGuidance(
                    nextInstruction = snapshot.nextInstruction,
                    distanceToNextMeters = snapshot.distanceToNextMeters,
                    remainingDistanceMeters = snapshot.remainingDistanceMeters,
                    remainingTimeSeconds = snapshot.remainingTimeSeconds,
                    speedLimitKmh = snapshot.speedLimitKmh,
                )
            }
        }
    }

    private fun buildNotification(text: String): android.app.Notification {
        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(tapIntent)
            .build()
    }

    override fun onDestroy() {
        (application as App).container.isaForwarder.stop()
        NavWidgetProvider.update(this, getString(R.string.widget_idle), null)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "navigation"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_STOP = "com.tomtom.demo.nav.action.STOP_NAV"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NavigationForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, NavigationForegroundService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
