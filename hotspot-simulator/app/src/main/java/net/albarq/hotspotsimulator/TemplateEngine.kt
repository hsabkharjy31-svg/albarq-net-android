package net.albarq.hotspotsimulator

object TemplateEngine {
    data class Context(val values: Map<String, String>, val flags: Map<String, Boolean>)
    private data class Frame(val parent: Boolean, var active: Boolean, var matched: Boolean)
    private val token = Regex("""\$\(([^)]*)\)""")

    fun render(source: String, c: Context): String {
        val out = StringBuilder()
        val stack = mutableListOf<Frame>()
        var active = true
        var cursor = 0
        for (m in token.findAll(source)) {
            if (active && m.range.first > cursor) out.append(source.substring(cursor, m.range.first))
            val body = m.groupValues[1].trim()
            when {
                body.startsWith("if ") -> {
                    val hit = eval(body.removePrefix("if ").trim(), c)
                    val f = Frame(active, active && hit, hit); stack.add(f); active = f.active
                }
                body.startsWith("elif ") -> stack.lastOrNull()?.let { f ->
                    val hit = !f.matched && eval(body.removePrefix("elif ").trim(), c)
                    f.active = f.parent && hit; f.matched = f.matched || hit; active = f.active
                }
                body == "else" -> stack.lastOrNull()?.let { f ->
                    f.active = f.parent && !f.matched; f.matched = true; active = f.active
                }
                body == "endif" -> if (stack.isNotEmpty()) active = stack.removeAt(stack.lastIndex).parent
                else -> if (active) out.append(c.values[body] ?: "$(" + body + ")")
            }
            cursor = m.range.last + 1
        }
        if (active && cursor < source.length) out.append(source.substring(cursor))
        return out.toString()
    }

    private fun eval(e: String, c: Context): Boolean {
        val x = e.trim()
        if (x.startsWith("!")) return !eval(x.drop(1), c)
        c.flags[x]?.let { return it }
        return when (x) {
            "error" -> !c.values["error"].isNullOrBlank()
            "chap-id" -> !c.values["chap-id"].isNullOrBlank()
            "chap-challenge" -> !c.values["chap-challenge"].isNullOrBlank()
            "login-by" -> !c.values["login-by"].isNullOrBlank()
            "trial" -> c.flags["trial"] == true
            "session-time-left" -> !c.values["session-time-left"].isNullOrBlank()
            else -> false
        }
    }

    fun defaults() = Context(mapOf(
        "link-login-only" to "https://hotspot.local/login", "link-login" to "https://hotspot.local/login",
        "link-orig" to "https://example.com/", "link-logout" to "https://hotspot.local/logout",
        "link-status" to "https://hotspot.local/status", "link-advert" to "https://hotspot.local/advert",
        "link-redirect" to "https://example.com/", "error" to "", "username" to "demo",
        "ip" to "192.0.2.10", "mac" to "02:00:00:00:00:10", "hostname" to "android-preview",
        "identity" to "AL-BARQ-TEST", "server-name" to "hotspot1", "interface-name" to "wlan1",
        "uptime" to "00:12:34", "session-time-left" to "00:47:26", "bytes-in" to "1048576",
        "bytes-out" to "2097152", "remain-bytes-total" to "104857600", "refresh-timeout" to "1m",
        "chap-id" to "", "chap-challenge" to ""
    ), mapOf("trial" to false))
}
