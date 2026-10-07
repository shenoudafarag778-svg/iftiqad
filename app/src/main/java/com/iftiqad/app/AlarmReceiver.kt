package com.iftiqad.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val s = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra("id", intent.getIntExtra("id", 0))
            putExtra("name", intent.getStringExtra("name") ?: "")
            putExtra("address", intent.getStringExtra("address") ?: "")
            putExtra("phone", intent.getStringExtra("phone") ?: "")
        }
        ContextCompat.startForegroundService(context, s)
    }
}
