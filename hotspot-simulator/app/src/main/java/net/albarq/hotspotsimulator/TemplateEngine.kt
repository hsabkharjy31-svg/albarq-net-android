package net.albarq.hotspotsimulator

object TemplateEngine {
    data class Context(
        val values: Map<String, String>,
        val flags: Map<String, Boolean> = emptyMap()
    )

    data class RenderResult(
        val html: String,
        val warnings: List<String>
    )

    private data class Frame(
        val parentActive: Boolean,
        var active: Boolean,
        var branchMatched: Boolean
    )

    private val placeholder = Regex("""\$\(([^)]*)\)""")
    private val expressionToken = Regex("""\s*(==|!=|&&|\|\||[()!]|'(?:\\.|[^'])*'|"(?:\\.|[^"])*"|[^\s()=!&|]+)""")

    fun render(source: String, context: Context): String =
        renderDetailed(source, context).html

    fun renderDetailed(source: String, context: Context): RenderResult {
        val output = StringBuilder()
        val warnings = linkedSetOf<String>()
        val stack = mutableListOf<Frame>()
        var active = true
        var cursor = 0

        for (match in placeholder.findAll(source)) {
            if (active && match.range.first > cursor) {
                output.append(source.substring(cursor, match.range.first))
            }

            val body = match.groupValues[1].trim()
            when {
                body.startsWith("if ") -> {
                    val hit = if (active) {
                        ConditionParser(body.removePrefix("if ").trim(), context, warnings).evaluate()
                    } else false
                    val frame = Frame(active, active && hit, hit)
                    stack.add(frame)
                    active = frame.active
                }

                body.startsWith("elif ") -> {
                    val frame = stack.lastOrNull()
                    if (frame == null) {
                        if (active) output.append(match.value)
                        warnings.add("كتلة elif بلا if مقابل.")
                    } else {
                        val hit = if (frame.parentActive && !frame.branchMatched) {
                            ConditionParser(body.removePrefix("elif ").trim(), context, warnings).evaluate()
                        } else false
                        frame.active = frame.parentActive && !frame.branchMatched && hit
                        frame.branchMatched = frame.branchMatched || hit
                        active = frame.active
                    }
                }

                body == "else" -> {
                    val frame = stack.lastOrNull()
                    if (frame == null) {
                        if (active) output.append(match.value)
                        warnings.add("كتلة else بلا if مقابل.")
                    } else {
                        frame.active = frame.parentActive && !frame.branchMatched
                        frame.branchMatched = true
                        active = frame.active
                    }
                }

                body == "endif" -> {
                    val frame = stack.removeLastOrNull()
                    if (frame == null) {
                        if (active) output.append(match.value)
                        warnings.add("كتلة endif بلا if مقابل.")
                    } else {
                        active = frame.parentActive
                    }
                }

                body.isBlank() -> if (active) output.append(match.value)

                else -> if (active) {
                    val value = context.values[body]
                    if (value != null) {
                        output.append(value)
                    } else {
                        output.append(match.value)
                        warnings.add("متغير RouterOS غير معروف أو غير مدعوم: " + body)
                    }
                }
            }

            cursor = match.range.last + 1
        }

        if (active && cursor < source.length) {
            output.append(source.substring(cursor))
        }
        if (stack.isNotEmpty()) {
            warnings.add("يوجد " + stack.size + " شرط if دون endif.")
        }
        return RenderResult(output.toString(), warnings.toList())
    }

    private class ConditionParser(
        expression: String,
        private val context: Context,
        private val warnings: MutableSet<String>
    ) {
        private val tokens = expressionToken.findAll(expression)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        private var cursor = 0

        fun evaluate(): Boolean {
            if (tokens.isEmpty()) {
                warnings.add("شرط if/elif فارغ.")
                return false
            }
            val result = parseOr()
            if (cursor < tokens.size) {
                warnings.add("تعذر تحليل بقية الشرط: " + tokens.drop(cursor).joinToString(" "))
            }
            return result
        }

        private fun parseOr(): Boolean {
            var result = parseAnd()
            while (consume("||")) {
                val right = parseAnd()
                result = result || right
            }
            return result
        }

        private fun parseAnd(): Boolean {
            var result = parseFactor()
            while (consume("&&")) {
                val right = parseFactor()
                result = result && right
            }
            return result
        }

        private fun parseFactor(): Boolean {
            if (consume("!")) return !parseFactor()
            if (consume("(")) {
                val result = parseOr()
                if (!consume(")")) warnings.add("قوس إغلاق مفقود في شرط RouterOS.")
                return result
            }

            val left = readValue()
            if (consume("==")) return left == readValue()
            if (consume("!=")) return left != readValue()
            return truthy(left)
        }

        private fun readValue(): String {
            val token = tokens.getOrNull(cursor) ?: return ""
            cursor++

            if ((token.startsWith("'") && token.endsWith("'")) ||
                (token.startsWith("\"") && token.endsWith("\""))) {
                return token.substring(1, token.length - 1)
                    .replace("\\'", "'")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }

            if (token.equals("true", true)) return "yes"
            if (token.equals("false", true)) return "no"
            if (token.equals("yes", true) || token.equals("no", true) ||
                token.equals("on", true) || token.equals("off", true) ||
                token.matches(Regex("""\d+(\.\d+)?"""))) {
                return token
            }

            context.flags[token]?.let { return if (it) "yes" else "no" }
            context.values[token]?.let { return it }

            if (token.matches(Regex("""[A-Za-z][A-Za-z0-9_-]*"""))) {
                warnings.add("متغير شرط غير معروف: " + token)
            }
            return ""
        }

        private fun consume(expected: String): Boolean {
            if (tokens.getOrNull(cursor) != expected) return false
            cursor++
            return true
        }

        private fun truthy(value: String): Boolean =
            value.isNotBlank() &&
                !value.equals("no", true) &&
                !value.equals("false", true) &&
                !value.equals("off", true) &&
                value != "0"
    }

    fun defaults() = Context(
        values = mapOf(
            "link-login" to "https://hotspot.local/login?dst=https%3A%2F%2Fexample.com%2F",
            "link-login-only" to "https://hotspot.local/login",
            "link-orig" to "https://example.com/",
            "link-orig-esc" to "https%3A%2F%2Fexample.com%2F",
            "link-redirect" to "https://example.com/",
            "link-redirect-esc" to "https%3A%2F%2Fexample.com%2F",
            "link-logout" to "https://hotspot.local/logout",
            "link-status" to "https://hotspot.local/status",
            "link-advert" to "https://hotspot.local/advert",
            "hostname" to "hotspot.local",
            "identity" to "AL-BARQ-TEST",
            "server-address" to "192.0.2.1:443",
            "server-name" to "hotspot1",
            "interface-name" to "wlan1",
            "domain" to "hotspot.local",
            "host-ip" to "192.0.2.10",
            "ip" to "192.0.2.10",
            "mac" to "02:00:00:00:00:10",
            "username" to "",
            "logged-in" to "no",
            "login-by" to "",
            "trial" to "no",
            "plain-passwd" to "yes",
            "ssl-login" to "yes",
            "idle-timeout" to "5m",
            "idle-timeout-secs" to "300",
            "session-timeout" to "",
            "session-timeout-secs" to "0",
            "session-time-left" to "",
            "session-time-left-secs" to "0",
            "uptime" to "",
            "uptime-secs" to "0",
            "refresh-timeout" to "",
            "refresh-timeout-secs" to "0",
            "limit-bytes-in" to "---",
            "limit-bytes-out" to "---",
            "remain-bytes-in" to "---",
            "remain-bytes-out" to "---",
            "remain-bytes-total" to "",
            "bytes-in" to "",
            "bytes-in-nice" to "",
            "bytes-out" to "",
            "bytes-out-nice" to "",
            "packets-in" to "",
            "packets-out" to "",
            "error" to "",
            "error-orig" to "",
            "chap-id" to "",
            "chap-challenge" to "",
            "popup" to "false",
            "advert-pending" to "no",
            "session-id" to "",
            "var" to "",
            "http-status" to "",
            "http-header" to "",
            "http-chap" to "no"
        ),
        flags = mapOf(
            "trial" to false,
            "logged-in" to false,
            "error" to false,
            "chap-id" to false,
            "chap-challenge" to false,
            "http-chap" to false,
            "session-time-left" to false,
            "remain-bytes-total" to false
        )
    )
}
