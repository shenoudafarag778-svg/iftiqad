package com.iftiqad.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Notifier {

    const val CHANNEL = "iftiqad_msg_v2"

    private fun ensureChannel(c: Context) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        try { nm.deleteNotificationChannel("iftiqad_alarm_v1") } catch (e: Exception) {}
        try { nm.deleteNotificationChannel("iftiqad_msg_v1") } catch (e: Exception) {}
        if (nm.getNotificationChannel(CHANNEL) == null) {
            val ch = NotificationChannel(CHANNEL, "تنبيهات الافتقاد", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "تنبيهات مواعيد الافتقاد"
            ch.enableVibration(true)
            ch.vibrationPattern = longArrayOf(0, 300, 200, 300)
            ch.enableLights(true)
            ch.setShowBadge(true)
            val attrs = android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            ch.setSound(
                android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION),
                attrs
            )
            ch.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(ch)
        }
    }

    fun timeText(t: Long): String =
        SimpleDateFormat("h:mm a", Locale("ar")).format(Date(t))

    fun post(c: Context, id: Int, title: String, text: String, big: String) {
        ensureChannel(c)
        val open = Intent(c, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_today", true)
        }
        val pi = PendingIntent.getActivity(
            c, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setColor(0xFF6366F1.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(big))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, n)
    }

    private fun countWord(n: Int): String = when {
        n == 1 -> "زيارة واحدة"
        n == 2 -> "زيارتين"
        n in 3..10 -> "$n زيارات"
        else -> "$n زيارة"
    }

    fun daily(c: Context) {
        try {
            val list = AlarmStore.todayVisits(c)
            if (list.isNotEmpty()) {
                val body = list.joinToString("\n") { "${timeText(it.second)} - ${it.first}" }
                post(
                    c, AlarmStore.DAILY_RC,
                    "📅 عندك ${countWord(list.size)} النهارده",
                    body.replace("\n", "  |  "), body
                )
            }
        } finally {
            AlarmStore.scheduleNextDaily(c)
        }
    }

    fun leadLabel(m: Int): String = when (m) {
        60 -> "ساعة"
        120 -> "ساعتين"
        180 -> "3 ساعات"
        else -> "$m دقيقة"
    }
}
