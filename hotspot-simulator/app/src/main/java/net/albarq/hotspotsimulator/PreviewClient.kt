package net.albarq.hotspotsimulator

import android.net.Uri
import android.webkit.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList

data class Diagnostic(val kind: String, val path: String, val message: String)

class PreviewClient(private val files: HotspotFiles) : WebViewClient() {
    val diagnostics = CopyOnWriteArrayList<Diagnostic>()
    private var loggedIn = false
    private var currentUsername = "demo"
    private var lastError = ""

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url ?: return null
        if (url.host != "hotspot.local") {
            diagnostics.add(Diagnostic("EXTERNAL", url.toString(), "طلب خارجي؛ لا يضمن المحاكي توفره دون إنترنت"))
            return blocked("المصادر الخارجية غير متاحة في وضع المعاينة المحلي")
        }
        val path = Uri.decode(url.encodedPath ?: "/").trimStart('/')
        if (path == "login" && url.getQueryParameter("var") == "callBack") return callbackResponse(request.isForMainFrame)
        when (path) {
            "login" -> return pageOrFallback("login.html")
            "status" -> return pageOrFallback("status.html")
            "logout" -> {
                loggedIn = false
                lastError = ""
                return pageOrFallback("logout.html")
            }
            "error" -> return pageOrFallback("error.html")
        }
        val actual = if (path.isBlank()) "login.html" else path
        val data = files.read(actual)
        if (data == null) {
            diagnostics.add(Diagnostic("404", actual, "لم يتم العثور على الملف. تحقق من المسار وحالة الأحرف."))
            return response("text/plain", ("404 — الملف غير موجود: " + actual).toByteArray(), 404, "Not Found")
        }
        return if (files.mime(actual).contains("html")) html(data, actual) else response(files.mime(actual), data)
    }

    fun simulateLogin(success: Boolean) {
        loggedIn = success
        currentUsername = "demo"
        lastError = if (success) "" else "اسم المستخدم أو كلمة المرور غير صحيحة (محاكاة محلية)"
    }

    fun submitCredentials(username: String, password: String) {
        currentUsername = username.trim().ifBlank { "demo" }
        // Local deterministic simulation only: any non-empty pair succeeds; credentials are never retained.
        loggedIn = username.isNotBlank() && password.isNotBlank()
        lastError = if (loggedIn) "" else "أدخل اسم المستخدم وكلمة المرور لمحاكاة الدخول."
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) diagnostics.add(
            Diagnostic("WEBVIEW", request.url.toString(), error?.description?.toString() ?: "فشل تحميل الصفحة")
        )
    }

    private fun pageOrFallback(name: String): WebResourceResponse {
        val data = files.read(name)
        if (data != null) return html(data, name)
        diagnostics.add(Diagnostic("MISSING", name, "ملف المسار غير موجود؛ تم إنشاء صفحة بديلة"))
        return response("text/html", ("<html dir='rtl'><meta charset='utf-8'><body><h3>" + name +
            "</h3><p>الملف غير موجود</p><a href='/login.html'>العودة</a></body></html>").toByteArray())
    }

    private fun html(data: ByteArray, path: String): WebResourceResponse {
        val base = TemplateEngine.defaults()
        val context = TemplateEngine.Context(
            base.values + mapOf(
                "username" to currentUsername,
                "error" to lastError,
                "logged-in" to loggedIn.toString(),
                "session-time-left" to if (loggedIn) base.values["session-time-left"].orEmpty() else ""
            ),
            base.flags + mapOf("trial" to false, "logged-in" to loggedIn)
        )
        var rendered = TemplateEngine.render(String(data, Charsets.UTF_8), context)
        if (path.substringAfterLast('/').equals("login.html", ignoreCase = true)) {
            val bridgeScript = """
                <script>
                (function () {
                  document.addEventListener('submit', function (event) {
                    var form = event.target;
                    if (!form || !form.elements || !form.elements.username || !form.elements.password) return;
                    event.preventDefault();
                    var dst = form.elements.dst ? form.elements.dst.value : '';
                    if (window.AlbarqPreview && window.AlbarqPreview.submitLogin) {
                      window.AlbarqPreview.submitLogin(form.elements.username.value, form.elements.password.value, dst);
                    }
                  }, true);
                })();
                </script>
            """.trimIndent()
            val closingBody = rendered.lastIndexOf("</body>", ignoreCase = true)
            rendered = if (closingBody >= 0) rendered.substring(0, closingBody) + bridgeScript + rendered.substring(closingBody)
            else rendered + bridgeScript
        }
        return response("text/html", rendered.toByteArray(Charsets.UTF_8))
    }

    private fun callbackResponse(mainFrame: Boolean): WebResourceResponse {
        val state = "{loggedIn:$loggedIn,username:${jsString(currentUsername)},error:${jsString(lastError)}}"
        val js = """
            (function () {
              var state = $state;
              window.mikrotikPreview = state;
              window.dispatchEvent(new CustomEvent('mikrotik-preview-ready', { detail: state }));
              if (typeof window.albarqMikrotikCallback === 'function') window.albarqMikrotikCallback(state);
            })();
        """.trimIndent()
        return if (mainFrame) {
            response("text/html", ("<!doctype html><html><meta charset='utf-8'><body><p>تم تنفيذ اختبار callback المحلي.</p><script>" +
                js + "</script><a href='/login.html'>العودة</a></body></html>").toByteArray(Charsets.UTF_8))
        } else {
            response("application/javascript", js.toByteArray(Charsets.UTF_8))
        }
    }

    private fun blocked(message: String) = response("text/plain", message.toByteArray(), 403, "Blocked")
    private fun response(mime: String, bytes: ByteArray, status: Int = 200, reason: String = "OK") =
        WebResourceResponse(mime, "UTF-8", status, reason, mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(bytes))

    private fun jsString(value: String) = "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "") + "'"
}
