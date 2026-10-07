package com.iftiqad.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AlarmService : Service() {

    companion object {
        const val ACTION_START = "com.iftiqad.app.START"
        const val ACTION_STOP = "com.iftiqad.app.STOP"
        const val ACTION_SNOOZE = "com.iftiqad.app.SNOOZE"
        const val ACTION_CLOSE = "com.iftiqad.app.CLOSE"
        const val CHANNEL = "iftiqad_alarm_v1"
        const val NID = 7001
    }

    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    private val keepRinging = object : Runnable {
        override fun run() {
            try {
                if (ringtone != null && ringtone?.isPlaying == false) ringtone?.play()
            } catch (e: Exception) {
            }
            handler.postDelayed(this, 3000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun str(i: Intent?, k: String): String = i?.getStringExtra(k) ?: ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopAll()
            }
            ACTION_SNOOZE -> {
                AlarmStore.schedule(
                    applicationContext,
                    intent?.getIntExtra("id", 0) ?: 0,
                    str(intent, "name"), str(intent, "address"), str(intent, "phone"),
                    System.currentTimeMillis() + 5 * 60 * 1000L,
                    false
                )
                stopAll()
            }
            else -> startAlarm(intent)
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(CHANNEL, "منبه الافتقاد", NotificationManager.IMPORTANCE_HIGH)
        ch.setSound(null, null)
        ch.enableVibration(false)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
    }

    private fun startAlarm(intent: Intent?) {
        val id = intent?.getIntExtra("id", 0) ?: 0
        val name = str(intent, "name")
        val address = str(intent, "address")
        val phone = str(intent, "phone")

        createChannel()

        val full = Intent(this, AlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("id", id)
            putExtra("name", name)
            putExtra("address", address)
            putExtra("phone", phone)
        }
        val flagsPi = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullPi = PendingIntent.getActivity(this, id, full, flagsPi)

        val stopPi = PendingIntent.getService(
            this, 1, Intent(this, AlarmService::class.java).setAction(ACTION_STOP), flagsPi
        )
        val snoozeIntent = Intent(this, AlarmService::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("id", id)
            putExtra("name", name)
            putExtra("address", address)
            putExtra("phone", phone)
        }
        val snoozePi = PendingIntent.getService(this, 2, snoozeIntent, flagsPi)

        val n = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("🔔 موعد افتقاد: $name")
            .setContentText("العنوان: $address  |  الهاتف: $phone")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("المخدوم: $name\nالعنوان: $address\nالهاتف: $phone")
            )
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullPi, true)
            .setContentIntent(fullPi)
            .addAction(0, "إيقاف المنبه", stopPi)
            .addAction(0, "غفوة 5 دقائق", snoozePi)
            .build()

        ServiceCompat.startForeground(
            this, NID, n,
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )

        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iftiqad:alarm")
            wakeLock?.acquire(5 * 60 * 1000L)
        } catch (e: Exception) {
        }

        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(
                AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0
            )
        } catch (e: Exception) {
        }

        try {
            ringtone?.stop()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= 28) ringtone?.isLooping = true
            ringtone?.play()
        } catch (e: Exception) {
        }

        try {
            @Suppress("DEPRECATION")
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val pattern = longArrayOf(0, 800, 500, 800, 1200)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } catch (e: Exception) {
        }

        handler.removeCallbacks(keepRinging)
        handler.postDelayed(keepRinging, 3000)
        handler.postDelayed({ stopAll() }, 5 * 60 * 1000L)

        try {
            startActivity(full)
        } catch (e: Exception) {
        }
    }

    private fun stopAll() {
        handler.removeCallbacksAndMessages(null)
        try { ringtone?.stop() } catch (e: Exception) {}
        ringtone = null
        try { vibrator?.cancel() } catch (e: Exception) {}
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (e: Exception) {}
        sendBroadcast(Intent(ACTION_CLOSE).setPackage(packageName))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        try { ringtone?.stop() } catch (e: Exception) {}
        try { vibrator?.cancel() } catch (e: Exception) {}
        super.onDestroy()
    }
}
