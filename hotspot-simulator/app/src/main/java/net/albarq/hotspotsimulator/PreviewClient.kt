package net.albarq.hotspotsimulator

import android.net.Uri
import android.webkit.*
import java.io.ByteArrayInputStream
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject

data class Diagnostic(val kind: String, val path: String, val message: String)

class PreviewClient(private val files: HotspotFiles) : WebViewClient() {
    val diagnostics = CopyOnWriteArrayList<Diagnostic>()
    private var loggedIn = false
    private var currentUsername = "demo"
    private var lastError = ""

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url
        if (url.host != "hotspot.local") {
            diagnostics.add(Diagnostic("EXTERNAL", url.toString(), "طلب خارجي؛ تم حجبه في المعاينة دون اتصال بالإنترنت"))
            return blocked("المصادر الخارجية غير متاحة في وضع المعاينة المحلي")
        }

        val path = Uri.decode(url.encodedPath ?: "/").trimStart('/')
        if (path in setOf("login", "status", "logout") && url.getQueryParameter("var") == "callBack") {
            return callbackResponse(path, req.isForMainFrame)
        }

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
            diagnostics.add(Diagnostic("404", actual, "لم يتم العثور على الملف. تحقق من المجلد المختار والمسار وحالة الأحرف."))
            return if (req.isForMainFrame && isHtmlPath(actual)) {
                response("text/html", missingHtml(actual).toByteArray(Charsets.UTF_8), 404, "Not Found")
            } else {
                response("text/plain", ("404 — الملف غير موجود: " + actual).toByteArray(Charsets.UTF_8), 404, "Not Found")
            }
        }

        val mime = files.mime(actual)
        return if (mime == "text/html" || mime == "application/xhtml+xml") {
            html(data, actual)
        } else {
            response(mime, data)
        }
    }

    fun simulateLogin(success: Boolean) {
        loggedIn = success
        currentUsername = "demo"
        lastError = if (success) "" else "اسم المستخدم أو كلمة المرور غير صحيحة (محاكاة محلية)"
    }

    fun submitCredentials(username: String, password: String) {
        currentUsername = username.trim().ifBlank { "demo" }
        // Card-only MikroTik templates commonly submit the card number in both fields.
        loggedIn = username.isNotBlank()
        lastError = if (loggedIn) "" else "أدخل رقم الكرت لمحاكاة الدخول."
    }

    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
        if (request?.isForMainFrame == true) diagnostics.add(
            Diagnostic("WEBVIEW", request.url.toString(), error?.description?.toString() ?: "فشل تحميل الصفحة")
        )
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        if (request?.isForMainFrame == true) diagnostics.add(
            Diagnostic("HTTP", request.url.toString(), "HTTP ${errorResponse?.statusCode ?: "unknown"}")
        )
    }

    private fun pageOrFallback(name: String): WebResourceResponse {
        val data = files.read(name)
        if (data != null) return html(data, name)
        diagnostics.add(Diagnostic("MISSING", name, "ملف الصفحة غير موجود؛ تم إنشاء صفحة HTML بديلة"))
        return response("text/html", missingHtml(name).toByteArray(Charsets.UTF_8))
    }

    private fun missingHtml(name: String) = """
        <!doctype html>
        <html lang="ar" dir="rtl">
        <head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>تعذر فتح الصفحة</title>
        <style>
          body{margin:0;min-height:100vh;display:grid;place-items:center;background:#071329;color:#fff;
          font-family:system-ui,sans-serif;padding:24px;box-sizing:border-box}
          main{max-width:520px;background:#112240;border:1px solid #29466e;border-radius:22px;padding:26px}
          h2{color:#38d5ff}p{line-height:1.8;color:#d6e2f5}code{direction:ltr;display:block;overflow-wrap:anywhere}
          a{display:inline-block;margin-top:12px;color:#071329;background:#38d5ff;padding:12px 18px;border-radius:12px;text-decoration:none;font-weight:700}
        </style></head>
        <body><main><h2>لم يتم العثور على صفحة القالب</h2>
        <p>تعذر العثور على الملف التالي داخل المجلد الذي اخترته:</p><code>${escapeHtml(name)}</code>
        <p>اختر المجلد الذي يحتوي على login.html، مع مجلدات css وjs وimg.</p>
        <a href="/login">إعادة المحاولة</a></main></body></html>
    """.trimIndent()

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
                    event.stopImmediatePropagation();
                    var username = form.elements.username.value || '';
                    var password = form.elements.password.value || username;
                    var dst = form.elements.dst ? form.elements.dst.value : '';
                    if (window.AlbarqPreview && window.AlbarqPreview.submitLogin) {
                      window.AlbarqPreview.submitLogin(username, password, dst);
                    }
                  }, true);
                })();
                </script>
            """.trimIndent()
            val closingBody = rendered.lastIndexOf("</body>", ignoreCase = true)
            rendered = if (closingBody >= 0) {
                rendered.substring(0, closingBody) + bridgeScript + rendered.substring(closingBody)
            } else rendered + bridgeScript
        }
        return response("text/html", rendered.toByteArray(Charsets.UTF_8))
    }

    private fun callbackResponse(path: String, mainFrame: Boolean): WebResourceResponse {
        if (path == "logout") {
            loggedIn = false
            lastError = ""
        }
        val action = when (path) {
            "status" -> "onStatusQuery"
            "logout" -> "onLoggedOut"
            else -> if (loggedIn) "onLoggedIn" else "onLoginStart"
        }
        val payload = JSONObject().apply {
            put("action", action)
            put("logged_in", loggedIn)
            put("username", currentUsername)
            put("error", lastError)
            put("ip", "192.0.2.10")
            put("mac", "02:00:00:00:00:10")
            put("uptime", "00:12:34")
            put("session_time_left", if (loggedIn) "00:47:26" else "")
            put("remain_bytes_total", if (loggedIn) "104857600" else "")
            put("bytes_in", "1048576")
            put("bytes_out", "2097152")
            put("sspeed", "512K/1024K_Uoff")
            put("update", "_Uoff")
        }
        return if (mainFrame) {
            val state = payload.toString().replace("<", "\\u003c")
            response("text/html", ("<!doctype html><html lang='ar' dir='rtl'><meta charset='utf-8'><body>" +
                "<p>تم تنفيذ callback المحلي.</p><script>window.mikrotikPreview=$state;</script>" +
                "<a href='/login'>العودة إلى صفحة الدخول</a></body></html>").toByteArray(Charsets.UTF_8))
        } else {
            response("application/json", payload.toString().toByteArray(Charsets.UTF_8))
        }
    }

    private fun isHtmlPath(path: String) =
        path.endsWith(".html", true) || path.endsWith(".htm", true) ||
            path.equals("login", true) || path.equals("status", true)

    private fun escapeHtml(value: String) = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    private fun blocked(message: String) =
        response("text/plain", message.toByteArray(Charsets.UTF_8), 403, "Blocked")

    private fun response(mime: String, bytes: ByteArray, status: Int = 200, reason: String = "OK") =
        WebResourceResponse(
            mime, "UTF-8", status, reason,
            mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
            ByteArrayInputStream(bytes)
        )

    private fun jsString(value: String) =
        "'" + value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "") + "'"
}
