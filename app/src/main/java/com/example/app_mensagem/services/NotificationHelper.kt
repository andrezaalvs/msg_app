package com.example.app_mensagem.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.app_mensagem.MainActivity
import com.example.app_mensagem.R
import kotlin.random.Random

object NotificationHelper {
    private const val CHANNEL_PREFIX = "messages_channel"
    private const val CHANNEL_NAME = "Mensagens Recebidas"

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        conversationId: String? = null,
        isHighPriority: Boolean = false,
        vibrationEnabled: Boolean = true
    ) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = "${CHANNEL_PREFIX}_${if (isHighPriority) "high" else "default"}_${if (vibrationEnabled) "vib" else "novib"}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = if (isHighPriority) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, CHANNEL_NAME, importance).apply {
                enableVibration(vibrationEnabled)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("conversationId", conversationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(if (vibrationEnabled) longArrayOf(0, 200, 120, 200) else longArrayOf(0))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(Random.nextInt(), notification)
    }
}