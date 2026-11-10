package net.albarq.hotspotsimulator

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class PreviewActivity : Activity() {
    private lateinit var files: HotspotFiles
    private lateinit var web: WebView
    private lateinit var progress: ProgressBar
    private lateinit var client: PreviewClient
    private val fileExecutor = Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("preview", MODE_PRIVATE) }
    private val navy = Color.rgb(7, 19, 41)
    private val cyan = Color.rgb(56, 213, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        files = HotspotFiles(this, prefs.getString("tree", null)?.let { Uri.parse(it) })
        buildPreviewScreen()
        indexAndLoad()
    }

    private fun buildPreviewScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(navy)
        }
        toolbar.addView(toolButton("‹", 30f) { finish() },
            LinearLayout.LayoutParams(dp(42), dp(46)))
        val heading = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), 0, 0, 0)
        }
        heading.addView(text("معاينة الهوتسبوت", 15f, Color.WHITE, true))
        heading.addView(text("LIVE LOCAL PREVIEW", 9f, cyan, true))
        toolbar.addView(heading, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(toolButton("⟳", 23f) { if (::web.isInitialized) web.reload() },
            LinearLayout.LayoutParams(dp(44), dp(44)))
        toolbar.addView(toolButton("⋮", 23f) { showDiagnostics() },
            LinearLayout.LayoutParams(dp(40), dp(44)))
        root.addView(toolbar)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(cyan)
            visibility = View.VISIBLE
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        client = PreviewClient(files)
        web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadsImagesAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
                }

                override fun onConsoleMessage(message: android.webkit.ConsoleMessage?): Boolean {
                    message?.let {
                        client.diagnostics.add(
                            Diagnostic("JAVASCRIPT", it.sourceId(), "السطر ${it.lineNumber()}: ${it.message()}")
                        )
                    }
                    return true
                }
            }
        }
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun submitLogin(username: String, password: String, destination: String) {
                runOnUiThread {
                    client.submitCredentials(username, password)
                    if (username.isNotBlank()) openPage("status")
                    else openPage("login")
                }
            }
        }, "AlbarqPreview")
        web.webViewClient = client
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun indexAndLoad() {
        if (files.tree == null) {
            Toast.makeText(this, "اختر مجلد القالب من الشاشة السابقة أولاً.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        progress.visibility = View.VISIBLE
        fileExecutor.execute {
            val paths = try { files.index() } catch (e: Exception) { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (paths.none { it.substringAfterLast('/').equals("login.html", true) }) {
                    Toast.makeText(this, "لم يتم العثور على login.html داخل المجلد المحدد.", Toast.LENGTH_LONG).show()
                }
                openPage("login")
            }
        }
    }

    private fun openPage(path: String) {
        if (::web.isInitialized) web.loadUrl("https://hotspot.local/" + path.trimStart('/'))
    }

    private fun showDiagnostics() {
        val warnings = files.scanWarnings
        val events = client.diagnostics.takeLast(35).map {
            "[${it.kind}] ${it.path}\n${it.message}"
        }
        val all = warnings + events
        AlertDialog.Builder(this)
            .setTitle("سجل المعاينة")
            .setMessage(if (all.isEmpty()) "لم تُسجل أخطاء حتى الآن." else all.joinToString("\n\n"))
            .setPositiveButton("إغلاق", null)
            .show()
    }

    private fun toolButton(caption: String, size: Float, action: () -> Unit) =
        TextView(this).apply {
            text = caption
            textSize = size
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }
        }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        fileExecutor.shutdownNow()
        if (::web.isInitialized) {
            web.removeJavascriptInterface("AlbarqPreview")
            web.stopLoading()
            web.destroy()
        }
        super.onDestroy()
    }
}
