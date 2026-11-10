package net.albarq.hotspotsimulator

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.*
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private lateinit var files: HotspotFiles
    private lateinit var web: WebView
    private lateinit var log: TextView
    private lateinit var client: PreviewClient
    private val fileExecutor = Executors.newSingleThreadExecutor()
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
        root.addView(button("اختيار مجلد الهوتسبوت") { chooseHotspotFolder() })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("فتح صفحة الدخول") { openPage("login") }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(button("تحديث المعاينة") { refreshFiles(openLoginAfterScan = true) }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)

        val tests = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tests.addView(button("محاكاة دخول ناجح") { client.simulateLogin(true); openPage("status") }, LinearLayout.LayoutParams(0, -2, 1f))
        tests.addView(button("محاكاة دخول فاشل") { client.simulateLogin(false); openPage("login.html") }, LinearLayout.LayoutParams(0, -2, 1f))
        tests.addView(button("اختبار callback") { openPage("login?var=callBack") }, LinearLayout.LayoutParams(0, -2, 1f))
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
        web.addJavascriptInterface(object {
            @JavascriptInterface
            fun submitLogin(username: String, password: String, destination: String) {
                runOnUiThread {
                    client.submitCredentials(username, password)
                    if (username.isNotBlank() && password.isNotBlank()) openPage("status")
                    else openPage("login.html")
                }
            }
        }, "AlbarqPreview")
        web.webViewClient = client
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        if (files.tree != null) {
            log.text = "تم استعادة المجلد المحفوظ. جارٍ فحص الملفات..."
            refreshFiles()
        }
    }

    private fun chooseHotspotFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            startActivityForResult(intent, REQUEST_HOTSPOT_FOLDER)
        } catch (e: Exception) {
            log.text = "تعذر فتح منتقي المجلدات: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    @Deprecated("Using the platform document picker for broad SAF provider compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_HOTSPOT_FOLDER || resultCode != RESULT_OK) return

        val uri = data?.data
        if (uri == null) {
            log.text = "لم يُرجع منتقي الملفات عنوان المجلد."
            return
        }

        val readGrant = (data.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        var persisted = false
        var permissionWarning = ""
        if (readGrant != 0) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                persisted = true
            } catch (e: SecurityException) {
                permissionWarning = "تعذر حفظ إذن الوصول الدائم؛ قد تحتاج لاختيار المجلد مرة أخرى بعد إغلاق التطبيق."
            } catch (e: IllegalArgumentException) {
                permissionWarning = "مزود الملفات لا يدعم حفظ الإذن الدائم؛ سيُستخدم الإذن الحالي مؤقتًا."
            }
        } else {
            permissionWarning = "لم يمنح مزود الملفات إذن قراءة واضحًا؛ قد لا يمكن قراءة المجلد."
        }

        files.tree = uri
        if (persisted) prefs.edit().putString("tree", uri.toString()).apply()
        else prefs.edit().remove("tree").apply()

        log.text = "تم اختيار المجلد. جارٍ فحص الملفات في الخلفية...\n$permissionWarning"
        refreshFiles(openLoginAfterScan = true, permissionWarning = permissionWarning)
    }

    private fun refreshFiles(openLoginAfterScan: Boolean = false, permissionWarning: String = "") {
        val selectedTree = files.tree
        if (selectedTree == null) {
            log.text = "يرجى اختيار مجلد الهوتسبوت أولاً."
            return
        }
        log.text = "جارٍ فحص ملفات مجلد الهوتسبوت..."
        fileExecutor.execute {
            val paths = try {
                files.index()
            } catch (e: Exception) {
                emptyList()
            }
            val warnings = files.scanWarnings
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val summary = buildString {
                    append("اكتمل الفحص. عدد الملفات: ").append(paths.size)
                    if (permissionWarning.isNotBlank()) append("\n").append(permissionWarning)
                    if (warnings.isNotEmpty()) {
                        append("\nتحذيرات الفهرسة:")
                        warnings.take(5).forEach { append("\n• ").append(it) }
                    }
                    if (paths.isNotEmpty()) {
                        append("\n").append(paths.take(12).joinToString("\n"))
                    } else if (warnings.isEmpty()) {
                        append("\nلم يتم العثور على ملفات قابلة للقراءة في المجلد المحدد.")
                        append("\nتأكد من اختيار المجلد الذي يحتوي على login.html أو المجلد الأب الذي يحتوي عليه.")
                    }
                }
                log.text = summary
                if (openLoginAfterScan) openPage("login")
            }
        }
    }

    private fun button(label: String, action: () -> Unit) =
        Button(this).apply { text = label; setOnClickListener { action() } }

    private fun openPage(path: String) {
        if (!::web.isInitialized) return
        if (files.tree == null) {
            log.text = "يرجى اختيار مجلد الهوتسبوت أولاً."
            return
        }
        // The SAF tree has already been indexed on a worker thread. Never walk the
        // provider tree synchronously from a WebView/UI callback.
        web.loadUrl("https://hotspot.local/" + path.trimStart('/'))
    }

    private fun showDiagnostics() {
        val items = if (::client.isInitialized) client.diagnostics.toList() else emptyList()
        val fileWarnings = files.scanWarnings
        val all = items.map { "[${it.kind}] ${it.path}: ${it.message}" } + fileWarnings
        log.text = if (all.isEmpty()) "لم تُسجل أخطاء حتى الآن." else all.joinToString("\n")
    }

    override fun onDestroy() {
        fileExecutor.shutdownNow()
        if (::web.isInitialized) {
            web.removeJavascriptInterface("AlbarqPreview")
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_HOTSPOT_FOLDER = 100
    }
}
