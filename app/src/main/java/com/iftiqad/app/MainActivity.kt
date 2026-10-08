package com.iftiqad.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import java.io.File

class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#070B14")
        window.navigationBarColor = Color.parseColor("#070B14")

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#070B14"))
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        val tab = intent.getStringExtra("open_tab") ?: ""
        val hash = if (tab.isNotEmpty()) "#$tab" else ""
        web.loadUrl("file:///android_asset/index.html$hash")

        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getStringExtra("open_tab") ?: ""
        if (tab == "today" || tab == "bday") {
            web.evaluateJavascript("window.goTab&&window.goTab('$tab')", null)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) {
            web.evaluateJavascript("window.onResumeApp&&window.onResumeApp()", null)
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        web.evaluateJavascript(
            "(function(){return window.onBack?window.onBack():false})()"
        ) { r ->
            if (r != "true") finish()
        }
    }

    private fun shareFile(f: File, mime: String, whatsapp: Boolean) {
        val uri = FileProvider.getUriForFile(this, "com.iftiqad.app.fileprovider", f)
        val i = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        if (whatsapp) {
            for (pkg in arrayOf("com.whatsapp", "com.whatsapp.w4b")) {
                try {
                    i.setPackage(pkg)
                    startActivity(i)
                    return
                } catch (e: ActivityNotFoundException) {
                }
            }
            i.setPackage(null)
        }
        startActivity(Intent.createChooser(i, "مشاركة"))
    }

    inner class Bridge {
        @JavascriptInterface
        fun scheduleVisit(id: String, time: String, name: String, phone: String, lead: String) {
            try {
                AlarmStore.scheduleVisit(
                    applicationContext, id.toInt(), time.toLong(), name, "", phone,
                    lead.toIntOrNull() ?: 0
                )
            } catch (e: Exception) {
                toast("تعذر ضبط التنبيه")
            }
        }

        @JavascriptInterface
        fun cancelAlarm(id: String) {
            try { AlarmStore.cancelVisit(applicationContext, id.toInt()) } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun cancelAllAlarms() {
            try { AlarmStore.cancelAll(applicationContext) } catch (e: Exception) {}
        }

        @JavascriptInterface
        fun setDaily(on: String, hour: String, minute: String) {
            try {
                AlarmStore.setDaily(
                    applicationContext, on == "1", hour.toIntOrNull() ?: 8, minute.toIntOrNull() ?: 0
                )
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun setBirthdays(json: String, on: String, pre: String, hour: String, minute: String) {
            try {
                AlarmStore.setBirthdays(
                    applicationContext, json, on == "1", pre == "1",
                    hour.toIntOrNull() ?: 9, minute.toIntOrNull() ?: 0
                )
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun getStatus(): String {
            return try {
                val notif = NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
                val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
                val bat = pm.isIgnoringBatteryOptimizations(packageName)
                "{\"notif\":$notif,\"battery\":$bat}"
            } catch (e: Exception) {
                "{\"notif\":true,\"battery\":false}"
            }
        }

        @JavascriptInterface
        fun openNotifSettings() {
            runOnUiThread {
                try {
                    startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    )
                } catch (e: Exception) {
                    toast("افتح إعدادات الهاتف ← التطبيقات ← تطبيق افتقاد ← الإشعارات")
                }
            }
        }

        @JavascriptInterface
        fun openBatterySettings() {
            runOnUiThread {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                            Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (e2: Exception) {
                        toast("افتح إعدادات الهاتف ← البطارية ← تطبيق افتقاد ← بدون قيود")
                    }
                }
            }
        }

        @JavascriptInterface
        fun testNotify() {
            try {
                AlarmStore.scheduleTest(applicationContext)
            } catch (e: Exception) {
                toast("تعذر ضبط التجربة")
            }
        }

        @JavascriptInterface
        fun dial(phone: String) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
            } catch (e: Exception) {
                toast("تعذر فتح الاتصال")
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                toast("تعذر فتح الرابط")
            }
        }

        @JavascriptInterface
        fun sharePdf(json: String, fileName: String, target: String) {
            try {
                val f = PdfBuilder.build(applicationContext, json, fileName)
                runOnUiThread {
                    try {
                        shareFile(f, "application/pdf", target == "wa")
                    } catch (e: Exception) {
                        toast("تعذرت المشاركة")
                    }
                }
            } catch (e: Exception) {
                toast("تعذر إنشاء الملف: ${e.message}")
            }
        }

        private fun toast(m: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, m, Toast.LENGTH_LONG).show() }
        }
    }
}
