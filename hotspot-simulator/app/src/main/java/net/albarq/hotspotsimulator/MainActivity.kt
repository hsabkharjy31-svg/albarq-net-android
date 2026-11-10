package net.albarq.hotspotsimulator

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.*

class MainActivity : Activity() {
    private lateinit var files: HotspotFiles
    private lateinit var web: WebView
    private lateinit var log: TextView
    private lateinit var client: PreviewClient
    private val prefs by lazy { getSharedPreferences("preview", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        files = HotspotFiles(this, prefs.getString("tree", null)?.let { Uri.parse(it) })
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 12, 14, 8)
            setBackgroundColor(Color.rgb(245, 248, 255))
        }
        val title = TextView(this).apply {
            text = "⚡ AL-BARQ NET\nمحاكي صفحات MikroTik Hotspot"
            textSize = 21f
            setTextColor(Color.rgb(13, 45, 105))
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
        }
        root.addView(title, LinearLayout.LayoutParams(-1, 72))
        root.addView(button("اختيار مجلد الهوتسبوت") { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), 100) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("فتح صفحة الدخول") { openPage("login.html") }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("تحديث المعاينة") { files.index(); openPage("login.html") }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        val tests = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tests.addView(button("محاكاة دخول ناجح") { client.simulateLogin(true); openPage("status") }, LinearLayout.LayoutParams(0, -2, 1f))
        tests.addView(button("محاكاة دخول فاشل") { client.simulateLogin(false); openPage("login.html") }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(tests)
        root.addView(button("فحص الصفحة / سجل الأخطاء") { showDiagnostics() })
        log = TextView(this).apply {
            text = "اختر مجلد صفحات الهوتسبوت للبدء.\nالمحاكاة محلية ولا تنشئ جلسة MikroTik حقيقية."
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }
        root.addView(ScrollView(this).apply { addView(log) }, LinearLayout.LayoutParams(-1, 94))
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webChromeClient = WebChromeClient()
        }
        client = PreviewClient(files)
        web.webViewClient = client
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        if (files.tree != null) log.text = "تم استعادة المجلد المحفوظ. اضغط فتح صفحة الدخول."
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
            files.tree = uri
            prefs.edit().putString("tree", uri.toString()).apply()
            val paths = files.index()
            log.text = "تم اختيار المجلد. عدد الملفات: " + paths.size + "\n" + paths.take(10).joinToString("\n")
        }
    }

    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; setOnClickListener { action() } }

    private fun openPage(path: String) {
        if (files.tree == null) { log.text = "يرجى اختيار مجلد الهوتسبوت أولاً"; return }
        files.index()
        web.loadUrl("https://hotspot.local/" + path.trimStart('/'))
    }

    private fun showDiagnostics() {
        val items = client.diagnostics.toList()
        log.text = if (items.isEmpty()) "لم تُسجل أخطاء حتى الآن." else items.joinToString("\n") { "[" + it.kind + "] " + it.path + ": " + it.message }
    }

    override fun onDestroy() {
        if (::web.isInitialized) web.destroy()
        super.onDestroy()
    }
}
