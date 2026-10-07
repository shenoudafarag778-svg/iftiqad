package com.iftiqad.app

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var web: WebView
    private var printView: WebView? = null

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
        web.loadUrl("file:///android_asset/index.html")

        askPermissions()
    }

    private fun askPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        } else {
            fullScreenCheck()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        fullScreenCheck()
    }

    private fun fullScreenCheck() {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val p = getSharedPreferences("iftiqad_main", Context.MODE_PRIVATE)
                if (!nm.canUseFullScreenIntent() && !p.getBoolean("fs_asked", false)) {
                    p.edit().putBoolean("fs_asked", true).apply()
                    startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
        } catch (e: Exception) {
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

    inner class Bridge {
        @JavascriptInterface
        fun scheduleAlarm(id: String, time: String, name: String, address: String, phone: String) {
            try {
                AlarmStore.schedule(
                    applicationContext, id.toInt(), name, address, phone, time.toLong()
                )
            } catch (e: Exception) {
                toast("تعذر ضبط المنبه")
            }
        }

        @JavascriptInterface
        fun cancelAlarm(id: String) {
            try {
                AlarmStore.cancel(applicationContext, id.toInt())
            } catch (e: Exception) {
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
        fun shareText(text: String) {
            try {
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                startActivity(Intent.createChooser(i, "مشاركة التقرير"))
            } catch (e: Exception) {
                toast("تعذرت المشاركة")
            }
        }

        @JavascriptInterface
        fun printHtml(html: String, title: String) {
            runOnUiThread {
                try {
                    val w = WebView(this@MainActivity)
                    w.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String?) {
                            val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
                            pm.print(
                                title,
                                view.createPrintDocumentAdapter(title),
                                PrintAttributes.Builder().build()
                            )
                        }
                    }
                    printView = w
                    w.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                } catch (e: Exception) {
                    toast("تعذر إنشاء التقرير")
                }
            }
        }

        private fun toast(m: String) {
            runOnUiThread { Toast.makeText(this@MainActivity, m, Toast.LENGTH_LONG).show() }
        }
    }
}
