package net.albarq.hotspotsimulator

import android.net.Uri
import android.webkit.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList

data class Diagnostic(val kind: String, val path: String, val message: String)

class PreviewClient(private val files: HotspotFiles) : WebViewClient() {
    val diagnostics = CopyOnWriteArrayList<Diagnostic>()
    private var loggedIn = false
    private var lastError = ""

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        if (url.host != "hotspot.local") {
            diagnostics.add(Diagnostic("EXTERNAL", url.toString(), "طلب خارجي؛ لا يضمن المحاكي توفره دون إنترنت"))
            return blocked("المصادر الخارجية غير متاحة في وضع المعاينة المحلي")
        }
        val path = Uri.decode(url.encodedPath ?: "/").trimStart('/')
        if (path == "login") {
            val js = "window.mikrotikPreview={loggedIn:" + loggedIn + ",error:" + jsString(lastError) + ",username:'demo'};window.dispatchEvent(new Event('mikrotik-preview-ready'));"
            return response("application/javascript", js.toByteArray())
        }
        if (path == "status" || path == "logout" || path == "error") {
            val target = when (path) { "status" -> "status.html"; "logout" -> "logout.html"; else -> "error.html" }
            val data = files.read(target)
            if (data != null) return html(data)
            diagnostics.add(Diagnostic("MISSING", target, "ملف المسار غير موجود؛ تم إنشاء صفحة بديلة"))
            return response("text/html", ("<html dir='rtl'><meta charset='utf-8'><body><h3>" + path + "</h3><p>الملف " + target + " غير موجود</p><a href='/login.html'>العودة</a></body></html>").toByteArray())
        }
        val actual = if (path.isBlank()) "login.html" else path
        val data = files.read(actual)
        if (data == null) {
            diagnostics.add(Diagnostic("404", actual, "لم يتم العثور على الملف. تحقق من المسار وحالة الأحرف."))
            return response("text/plain", ("404 — الملف غير موجود: " + actual).toByteArray(), 404, "Not Found")
        }
        return if (files.mime(actual).contains("html")) html(data) else response(files.mime(actual), data)
    }

    fun simulateLogin(success: Boolean) {
        loggedIn = success
        lastError = if (success) "" else "اسم المستخدم أو كلمة المرور غير صحيحة (محاكاة محلية)"
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) diagnostics.add(Diagnostic("WEBVIEW", request.url.toString(), error?.description?.toString() ?: "فشل تحميل الصفحة"))
    }

    private fun html(data: ByteArray): WebResourceResponse {
        val rendered = TemplateEngine.render(String(data, Charsets.UTF_8), TemplateEngine.defaults())
        return response("text/html", rendered.toByteArray(Charsets.UTF_8))
    }

    private fun blocked(message: String) = response("text/plain", message.toByteArray(), 403, "Blocked")
    private fun response(mime: String, bytes: ByteArray, status: Int = 200, reason: String = "OK") =
        WebResourceResponse(mime, "UTF-8", status, reason, mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(bytes))
    private fun jsString(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "") + "'"
}
