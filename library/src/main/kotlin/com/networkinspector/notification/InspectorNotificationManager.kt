package com.networkinspector.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.networkinspector.R
import com.networkinspector.core.NetworkInspectorConfig
import com.networkinspector.core.RequestStats
import com.networkinspector.ui.RequestListActivity

/**
 * Manages notifications for NetworkInspector.
 * Shows a persistent notification with request statistics.
 */
internal class InspectorNotificationManager(
    private val context: Context,
    private val config: NetworkInspectorConfig
) {
    
    companion object {
        private const val CHANNEL_ID = "network_inspector_channel"
        private const val NOTIFICATION_ID = 1991
    }
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var isNotificationShown = false
    
    init {
        createNotificationChannel()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                config.notificationChannelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows network request activity"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Update the notification with current stats
     */
    fun updateNotification(stats: RequestStats) {
        mainHandler.post {
            showNotification(stats)
        }
    }
    
    private fun showNotification(stats: RequestStats) {
        val contentText = buildString {
            append("${stats.active} active")
            append(" • ${stats.successful} success")
            if (stats.failed > 0) append(" • ${stats.failed} failed")
        }
        
        val expandedText = buildString {
            appendLine("Active: ${stats.active}")
            appendLine("Completed: ${stats.completed}")
            appendLine("Success: ${stats.successful}")
            append("Failed: ${stats.failed}")
        }
        
        val intent = Intent(context, RequestListActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_network_inspector)
            .setContentTitle("Network Inspector")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setContentIntent(pendingIntent)
            .setOngoing(stats.active > 0)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(createClearAction())
            .build()
        
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            isNotificationShown = true
        } catch (e: SecurityException) {
            // Notification permission not granted
        }
    }
    
    private fun createClearAction(): NotificationCompat.Action {
        val clearIntent = Intent(context, ClearRequestsReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            clearIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Action.Builder(
            0,
            "Clear",
            pendingIntent
        ).build()
    }
    
    /**
     * Dismiss the notification
     */
    fun dismiss() {
        mainHandler.post {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            isNotificationShown = false
        }
    }
}




