package com.iftiqad.app

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat

class AlarmActivity : Activity() {

    private val closer = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        ContextCompat.registerReceiver(
            this, closer, IntentFilter(AlarmService.ACTION_CLOSE),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        build(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        build(intent)
    }

    override fun onDestroy() {
        try { unregisterReceiver(closer) } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun tv(text: String, size: Float, bold: Boolean = false): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
    }

    private fun btn(text: String, bg: Int, fg: Int): Button {
        return Button(this).apply {
            this.text = text
            textSize = 22f
            setTextColor(fg)
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(bg)
                cornerRadius = dp(18).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(76)
            ).apply { topMargin = dp(18) }
        }
    }

    private fun build(i: Intent) {
        val id = i.getIntExtra("id", 0)
        val name = i.getStringExtra("name") ?: ""
        val address = i.getStringExtra("address") ?: ""
        val phone = i.getStringExtra("phone") ?: ""

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B91C1C"))
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutDirection = View.LAYOUT_DIRECTION_RTL
        }
        root.addView(tv("🔔", 64f))
        root.addView(tv("موعد افتقاد", 30f, true))
        root.addView(tv(name, 34f, true))
        root.addView(tv("العنوان: $address", 20f))
        root.addView(tv("الهاتف: $phone", 22f, true))

        val stop = btn("إيقاف المنبه", Color.WHITE, Color.parseColor("#B91C1C"))
        stop.setOnClickListener {
            startService(Intent(this, AlarmService::class.java).setAction(AlarmService.ACTION_STOP))
            finish()
        }
        val snooze = btn("غفوة 5 دقائق", Color.parseColor("#7F1D1D"), Color.WHITE)
        snooze.setOnClickListener {
            startService(Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_SNOOZE
                putExtra("id", id)
                putExtra("name", name)
                putExtra("address", address)
                putExtra("phone", phone)
            })
            finish()
        }
        root.addView(stop)
        root.addView(snooze)
        setContentView(root)
    }
}
