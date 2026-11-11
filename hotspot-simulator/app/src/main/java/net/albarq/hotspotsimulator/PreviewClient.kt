package net.albarq.hotspotsimulator

import android.net.Uri
import android.webkit.*
import java.io.ByteArrayInputStream
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
import org.json.JSONObject

data class Diagnostic(val kind: String, val path: String, val message: String)

class PreviewClient(private val files: HotspotFiles) : WebViewClient() {
    val diagnostics = CopyOnWriteArrayList<Diagnostic>()

    @Volatile private var loggedIn = false
    @Volatile private var currentUsername = ""
    @Volatile private var lastError = ""
    @Volatile private var loginBy = ""
    @Volatile private var originalDestination = "https://example.com/"
    @Volatile private var sessionStartedAtMs = 0L

    companion object {
        private const val SESSION_LIMIT_SECONDS = 3600L
        private const val DEMO_BYTES_IN = 1048576L
        private const val DEMO_BYTES_OUT = 2097152L
        private const val DEMO_REMAIN_BYTES = 104857600L
        private const val MAX_DIAGNOSTICS = 200
    }

    fun recordDiagnostic(kind: String, path: String, message: String) {
        if (diagnostics.size >= MAX_DIAGNOSTICS) {
            diagnostics.removeAt(0)
        }
        diagnostics.add(Diagnostic(kind, path, message))
    }

    @Synchronized
    fun submitCredentials(username: String, password: String, destination: String = ""): Boolean {
        val candidate = username.trim().ifBlank { password.trim() }
        if (candidate.isBlank()) {
            lastError = "أدخل اسم المستخدم أو رقم الكرت لمحاكاة الدخول."
            recordDiagnostic("LOGIN", "/login", "رفض المحاكاة لأن حقلي اسم المستخدم وكلمة المرور فارغان.")
            return false
        }

        currentUsername = candidate
        originalDestination = destination.trim().ifBlank { originalDestination }
        loggedIn = true
        loginBy = "http-pap"
        lastError = ""
        sessionStartedAtMs = System.currentTimeMillis()
        // The password is intentionally not retained or written to diagnostics.
        return true
    }

    @Synchronized
    private fun logout() {
        loggedIn = false
        currentUsername = ""
        loginBy = ""
        lastError = ""
        sessionStartedAtMs = 0L
    }

    @Synchronized
    fun stateJson(): String = stateObject().toString()

    @Synchronized
    private fun stateObject(): JSONObject {
        val elapsed = elapsedSeconds()
        val remaining = if (loggedIn) (SESSION_LIMIT_SECONDS - elapsed).coerceAtLeast(0L) else 0L
        return JSONObject()
            .put("logged_in", loggedIn)
            .put("logged-in", if (loggedIn) "yes" else "no")
            .put("username", currentUsername)
            .put("error", lastError)
            .put("login_by", loginBy)
            .put("ip", "192.0.2.10")
            .put("mac", "02:00:00:00:00:10")
            .put("hostname", "hotspot.local")
            .put("identity", "AL-BARQ-TEST")
            .put("server_name", "hotspot1")
            .put("interface_name", "wlan1")
            .put("uptime", if (loggedIn) formatDuration(elapsed) else "")
            .put("uptime_secs", if (loggedIn) elapsed else 0L)
            .put("session_time_left", if (loggedIn) formatDuration(remaining) else "")
            .put("session_time_left_secs", remaining)
            .put("remain_bytes_total", if (loggedIn) DEMO_REMAIN_BYTES else 0L)
            .put("bytes_in", if (loggedIn) DEMO_BYTES_IN else 0L)
            .put("bytes_out", if (loggedIn) DEMO_BYTES_OUT else 0L)
            .put("packets_in", if (loggedIn) 251 else 0)
            .put("packets_out", if (loggedIn) 211 else 0)
    }

    private fun elapsedSeconds(): Long =
        if (loggedIn && sessionStartedAtMs > 0L) {
            ((System.currentTimeMillis() - sessionStartedAtMs) / 1000L).coerceAtLeast(0L)
        } else 0L

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600L
        val minutes = (seconds % 3600L) / 60L
        val remainder = seconds % 60L
        return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, remainder)
    }

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val req = request ?: return null
        val url = req.url

        if (url.scheme == "data" || url.scheme == "about" || url.scheme == "blob") return null
        if (url.host != "hotspot.local") {
            recordDiagnostic(
                "EXTERNAL",
                url.toString(),
                "طلب خارجي محجوب في وضع المعاينة المحلي."
            )
            return blocked("المصادر الخارجية غير متاحة في وضع المعاينة المحلي.")
        }

        val path = Uri.decode(url.encodedPath ?: "").trimStart('/')
        if (path.split('/').any { it == ".." }) {
            recordDiagnostic("PATH", path, "تم حجب مسار يتجاوز مجلد القالب.")
            return blocked("مسار غير مسموح.")
        }

        if (path in setOf("login", "status", "logout") &&
            url.getQueryParameter("var").equals("callBack", ignoreCase = true)) {
            return callbackResponse(path)
        }

        if (path == "api") return captivePortalApiResponse()

        if (req.method.equals("POST", ignoreCase = true) || req.method.equals("PUT", ignoreCase = true)) {
            recordDiagnostic(
                "METHOD",
                url.toString(),
                "لم يصل طلب النموذج عبر جسر JavaScript؛ لا يتيح WebResourceRequest قراءة جسم POST."
            )
            return response(
                "text/plain",
                "تعذر محاكاة POST مباشرة. أرسل النموذج من صفحة المعاينة ليعمل جسر تسجيل الدخول المحلي.".toByteArray(Charsets.UTF_8),
                405,
                "Method Not Allowed"
            )
        }

        return when (path) {
            "" -> if (loggedIn) pageOrFallback("rstatus.html", "status.html")
                 else pageOrFallback("rlogin.html", "login.html")

            "login" -> when {
                loggedIn -> pageOrFallback("alogin.html", "status.html")
                lastError.isNotBlank() -> pageOrFallback("flogin.html", "login.html")
                else -> pageOrFallback("login.html")
            }

            "status" -> if (loggedIn) pageOrFallback("status.html")
                        else pageOrFallback("fstatus.html", "login.html")

            "logout" -> if (loggedIn) {
                val page = pageOrFallback("logout.html", "login.html")
                logout()
                page
            } else {
                pageOrFallback("flogout.html", "login.html")
            }

            "error" -> pageOrFallback("error.html", "login.html")
            "advert" -> pageOrFallback("radvert.html", "status.html")
            else -> {
                val actual = path.ifBlank { "login.html" }
                val data = files.read(actual)
                if (data == null) {
                    recordDiagnostic(
                        "404",
                        actual,
                        "لم يتم العثور على الملف. تحقق من المجلد المختار والمسار وحالة الأحرف."
                    )
                    if (req.isForMainFrame && isHtmlPath(actual)) {
                        response("text/html", missingHtml(actual).toByteArray(Charsets.UTF_8), 404, "Not Found")
                    } else {
                        response(
                            "text/plain",
                            ("404 — الملف غير موجود: " + actual).toByteArray(Charsets.UTF_8),
                            404,
                            "Not Found"
                        )
                    }
                } else {
                    val mime = files.mime(actual)
                    if (mime == "text/html" || mime == "application/xhtml+xml") html(data, actual)
                    else response(mime, data)
                }
            }
        }
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        recordDiagnostic(
            "WEBVIEW",
            request?.url?.toString() ?: "unknown",
            error?.description?.toString() ?: "فشل تحميل المورد."
        )
    }

    override fun onReceivedHttpError(
        view: WebView?,
        request: WebResourceRequest?,
        errorResponse: WebResourceResponse?
    ) {
        recordDiagnostic(
            "HTTP",
            request?.url?.toString() ?: "unknown",
            "HTTP " + (errorResponse?.statusCode ?: "unknown") +
                if (request?.isForMainFrame == true) " (الصفحة الرئيسية)" else " (مورد فرعي)"
        )
    }

    private fun pageOrFallback(name: String, fallback: String? = null): WebResourceResponse {
        val data = files.read(name)
        if (data != null) return html(data, name)

        if (!fallback.isNullOrBlank() && fallback != name) {
            val fallbackData = files.read(fallback)
            if (fallbackData != null) {
                recordDiagnostic("FALLBACK", name, "الملف غير موجود؛ عُرض البديل " + fallback + ".")
                return html(fallbackData, fallback)
            }
        }

        recordDiagnostic("MISSING", name, "ملف الصفحة غير موجود؛ تم إنشاء صفحة HTML تشخيصية.")
        return response("text/html", missingHtml(name).toByteArray(Charsets.UTF_8))
    }

    @Synchronized
    private fun contextForPage(): TemplateEngine.Context {
        val base = TemplateEngine.defaults()
        val elapsed = elapsedSeconds()
        val remaining = if (loggedIn) (SESSION_LIMIT_SECONDS - elapsed).coerceAtLeast(0L) else 0L
        val original = originalDestination
        val values = base.values + mapOf(
            "link-login" to ("https://hotspot.local/login?dst=" + Uri.encode(original)),
            "link-login-only" to "https://hotspot.local/login",
            "link-orig" to original,
            "link-orig-esc" to Uri.encode(original),
            "link-redirect" to original,
            "link-redirect-esc" to Uri.encode(original),
            "link-logout" to "https://hotspot.local/logout",
            "link-status" to "https://hotspot.local/status",
            "link-advert" to "https://hotspot.local/advert",
            "username" to currentUsername,
            "error" to lastError,
            "error-orig" to lastError,
            "logged-in" to if (loggedIn) "yes" else "no",
            "login-by" to loginBy,
            "trial" to "no",
            "plain-passwd" to "yes",
            "ssl-login" to "yes",
            "session-timeout" to if (loggedIn) formatDuration(remaining) else "",
            "session-timeout-secs" to if (loggedIn) remaining.toString() else "0",
            "session-time-left" to if (loggedIn) formatDuration(remaining) else "",
            "session-time-left-secs" to if (loggedIn) remaining.toString() else "0",
            "uptime" to if (loggedIn) formatDuration(elapsed) else "",
            "uptime-secs" to if (loggedIn) elapsed.toString() else "0",
            "refresh-timeout" to if (loggedIn) "1m30s" else "",
            "refresh-timeout-secs" to if (loggedIn) "90" else "0",
            "remain-bytes-total" to if (loggedIn) DEMO_REMAIN_BYTES.toString() else "",
            "bytes-in" to if (loggedIn) DEMO_BYTES_IN.toString() else "",
            "bytes-in-nice" to if (loggedIn) "1 MiB" else "",
            "bytes-out" to if (loggedIn) DEMO_BYTES_OUT.toString() else "",
            "bytes-out-nice" to if (loggedIn) "2 MiB" else "",
            "packets-in" to if (loggedIn) "251" else "",
            "packets-out" to if (loggedIn) "211" else "",
            "session-id" to if (loggedIn) "albarq-demo-session" else "",
            "host-ip" to "192.0.2.10",
            "var" to "",
            "popup" to "false",
            "advert-pending" to "no",
            "http-chap" to "no"
        )
        val flags = base.flags + mapOf(
            "trial" to false,
            "logged-in" to loggedIn,
            "error" to lastError.isNotBlank(),
            "chap-id" to false,
            "chap-challenge" to false,
            "http-chap" to false,
            "session-time-left" to (loggedIn && remaining > 0L),
            "remain-bytes-total" to loggedIn
        )
        return TemplateEngine.Context(values, flags)
    }

    private fun html(data: ByteArray, path: String): WebResourceResponse {
        val source = String(data, Charsets.UTF_8)
        val result = TemplateEngine.renderDetailed(source, contextForPage())
        result.warnings.forEach { recordDiagnostic("TEMPLATE", path, it) }

        if (source.contains("$(chap-id)") || source.contains("$(chap-challenge)") ||
            source.contains("$(if http-chap)")) {
            recordDiagnostic(
                "CHAP",
                path,
                "القالب يطلب HTTP-CHAP؛ لم تُنفّذ مصادقة CHAP/MD5 في المحاكي، ويجب استخدام PAP/قالب تجريبي."
            )
        }

        val rendered = injectBridge(result.html)
        return response("text/html", rendered.toByteArray(Charsets.UTF_8))
    }

    private fun injectBridge(source: String): String {
        val script = bridgeScript()
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(source)
        return if (head != null) {
            source.substring(0, head.range.last + 1) + script + source.substring(head.range.last + 1)
        } else {
            script + source
        }
    }

    private fun bridgeScript(): String {
        val safeState = stateJson()
            .replace("<", "\\u003c")
            .replace(">", "\\u003e")
            .replace("&", "\\u0026")
        return """
        <script id="albarq-preview-bridge">
        (function () {
          if (window.__albarqPreviewInstalled) return;
          window.__albarqPreviewInstalled = true;

          var api = window.mikrotikPreview = window.mikrotikPreview || {};
          api.state = __INITIAL_STATE__;
          api.keepSinglePage = false;
          api.dispatch = function (name, detail) {
            try {
              document.dispatchEvent(new CustomEvent(name, {
                detail: detail || {},
                bubbles: true
              }));
            } catch (error) { console.error('Preview event failed', error); }
          };
          document.addEventListener('DOMContentLoaded', function () {
            api.dispatch('mikrotik-preview-ready', api.state);
          }, { once: true });
          api.requestCallback = function (path, callback) {
            var target = path || window.location.pathname;
            var join = target.indexOf('?') >= 0 ? '&' : '?';
            var xhr = new XMLHttpRequest();
            xhr.open('GET', target + join + 'var=callBack', true);
            xhr.onreadystatechange = function () {
              if (xhr.readyState !== 4) return;
              var payload = null;
              try { payload = JSON.parse(xhr.responseText); } catch (error) {}
              if (typeof callback === 'function') callback(payload, xhr.status);
            };
            xhr.send(null);
            return xhr;
          };

          function isLocalLogin(form) {
            try {
              var action = new URL(form.getAttribute('action') || window.location.href, window.location.href);
              return action.hostname === 'hotspot.local' &&
                (action.pathname === '/login' || action.pathname === '/login.html' || action.pathname === '/');
            } catch (error) { return false; }
          }

          function field(form, name) {
            var item = form.elements.namedItem(name);
            if (!item) return '';
            return typeof item.value === 'string' ? item.value : '';
          }

          function submitLocal(form) {
            if (form.__albarqPreviewSubmitted) return;
            form.__albarqPreviewSubmitted = true;
            if (!window.AlbarqPreview || typeof window.AlbarqPreview.submitLogin !== 'function') {
              api.dispatch('mikrotik-preview-error', { message: 'Native preview bridge is unavailable' });
              return;
            }
            window.AlbarqPreview.submitLogin(
              field(form, 'username'),
              field(form, 'password'),
              field(form, 'dst')
            );
          }

          document.addEventListener('submit', function (event) {
            var form = event.target;
            if (!form || !isLocalLogin(form)) return;
            event.preventDefault();
            // Let template onsubmit handlers run first (for example, CHAP hashing),
            // then read the final field values without sending a real network POST.
            window.setTimeout(function () { submitLocal(form); }, 0);
          }, true);

          var originalSubmit = HTMLFormElement.prototype.submit;
          HTMLFormElement.prototype.submit = function () {
            if (isLocalLogin(this)) { submitLocal(this); return; }
            return originalSubmit.call(this);
          };
        })();
        </script>
    """.trimIndent().replace("__INITIAL_STATE__", safeState)
    }

    private fun captivePortalApiResponse(): WebResourceResponse {
        val state = stateObject()
        val payload = JSONObject()
            .put("captive", !loggedIn)
            .put("user-portal-url", "https://hotspot.local/login")
            .put("can-extend-session", true)

        if (loggedIn) {
            payload.put("seconds-remaining", state.optLong("session_time_left_secs"))
            payload.put("bytes-remaining", DEMO_REMAIN_BYTES)
        }
        return response("application/json", payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun callbackResponse(path: String): WebResourceResponse {
        if (path == "logout") logout()
        val state = stateObject()
        val action = when (path) {
            "status" -> "onStatusQuery"
            "logout" -> "onLoggedOut"
            else -> if (lastError.isNotBlank()) "onLoginError"
                    else if (loggedIn) "onLoggedIn" else "onLoginStart"
        }
        val payload = JSONObject()
            .put("action", action)
            .put("event", action)
            .put("state", state)
            .put("logged_in", state.optBoolean("logged_in"))
            .put("logged-in", state.optString("logged-in"))
            .put("username", state.optString("username"))
            .put("error", state.optString("error"))
            .put("ip", state.optString("ip"))
            .put("mac", state.optString("mac"))
            .put("uptime", state.optString("uptime"))
            .put("session_time_left", state.optString("session_time_left"))
            .put("remain_bytes_total", state.optLong("remain_bytes_total"))
            .put("bytes_in", state.optLong("bytes_in"))
            .put("bytes_out", state.optLong("bytes_out"))
            .put("login_by", state.optString("login_by"))

        return response("application/json", payload.toString().toByteArray(Charsets.UTF_8))
    }

    private fun missingHtml(name: String): String = """
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
        <p>تعذر العثور على الملف التالي داخل المجلد الذي اخترته:</p><code>__PATH__</code>
        <p>اختر المجلد الذي يحتوي على login.html، مع مجلدات css وjs وimg.</p>
        <a href="/login">إعادة المحاولة</a></main></body></html>
    """.trimIndent().replace("__PATH__", escapeHtml(name))

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
            mime,
            "UTF-8",
            status,
            reason,
            mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
            ByteArrayInputStream(bytes)
        )
}
