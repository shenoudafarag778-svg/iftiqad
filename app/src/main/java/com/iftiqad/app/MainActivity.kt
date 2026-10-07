package com.iftiqad.app

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
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
        val hash = if (intent.getBooleanExtra("open_today", false)) "#today" else ""
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
        if (intent.getBooleanExtra("open_today", false)) {
            web.evaluateJavascript("window.goToday&&window.goToday()", null)
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

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode == 77 && resultCode == RESULT_OK && uri != null) {
            try {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                web.evaluateJavascript("window.onBackupData&&window.onBackupData('$b64')", null)
            } catch (e: Exception) {
                Toast.makeText(this, "تعذرت قراءة الملف", Toast.LENGTH_LONG).show()
            }
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
        fun scheduleVisit(
            id: String, time: String, name: String, address: String, phone: String, lead: String
        ) {
            try {
                AlarmStore.scheduleVisit(
                    applicationContext, id.toInt(), time.toLong(), name, address, phone,
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
        fun dial(phone: String) {
            try {
                startActivity(Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
            } catch (e: Exception) {
                toast("تعذر فتح الاتصال")
            }
        }

        @JavascriptInterface
        fun openUrl(url: String) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
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

        @JavascriptInterface
        fun exportBackup(text: String, fileName: String) {
            try {
                val dir = File(cacheDir, "reports")
                dir.mkdirs()
                val f = File(dir, fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                f.writeText(text, Charsets.UTF_8)
                runOnUiThread {
                    try {
                        shareFile(f, "application/json", false)
                    } catch (e: Exception) {
                        toast("تعذرت المشاركة")
                    }
                }
            } catch (e: Exception) {
                toast("تعذر إنشاء النسخة الاحتياطية")
            }
        }

        @JavascriptInterface
        fun pickBackup() {
            runOnUiThread {
                try {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    @Suppress("DEPRECATION")
                    startActivityForResult(i, 77)
                } catch (e: Exception) {
                    toast("تعذر فتح اختيار الملفات")
                }
            }
        }

        private fun toast(m: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, m, Toast.LENGTH_LONG).show() }
        }
    }
}
