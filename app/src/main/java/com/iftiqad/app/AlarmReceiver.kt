package com.iftiqad.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = intent.getStringExtra("kind") ?: "visit"
        if (kind == "daily") {
            Notifier.daily(context)
            return
        }
        val rc = intent.getIntExtra("rc", 0)
        val name = intent.getStringExtra("name") ?: ""
        val address = intent.getStringExtra("address") ?: ""
        val phone = intent.getStringExtra("phone") ?: ""
        val lead = intent.getIntExtra("lead", 0)
        val vt = intent.getLongExtra("vt", 0L)

        val title: String
        val big: String
        if (kind == "pre") {
            title = "⏳ بعد ${Notifier.leadLabel(lead)}: افتقاد $name"
            big = "المخدوم: $name\nالموعد: ${Notifier.timeText(vt)}\nالعنوان: $address\nالهاتف: $phone"
        } else {
            title = "🔔 موعد افتقاد: $name"
            big = "المخدوم: $name\nالعنوان: $address\nالهاتف: $phone"
        }
        Notifier.post(context, rc, title, "العنوان: $address  |  الهاتف: $phone", big)
    }
}
