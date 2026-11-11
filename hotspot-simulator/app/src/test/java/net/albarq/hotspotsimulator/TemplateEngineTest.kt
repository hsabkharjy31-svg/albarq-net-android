package net.albarq.hotspotsimulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateEngineTest {
    @Test
    fun rendersKnownVariablesAndPreservesUnknownOnes() {
        val context = TemplateEngine.Context(
            values = mapOf("username" to "demo", "ip" to "192.0.2.10")
        )

        val result = TemplateEngine.renderDetailed(
            "user=$(username); ip=$(ip); unknown=$(not-a-routeros-variable)",
            context
        )

        assertEquals(
            "user=demo; ip=192.0.2.10; unknown=$(not-a-routeros-variable)",
            result.html
        )
        assertTrue(result.warnings.any { it.contains("not-a-routeros-variable") })
    }

    @Test
    fun supportsNestedIfElseAndRouterOsComparisons() {
        val context = TemplateEngine.Context(
            values = mapOf(
                "logged-in" to "yes",
                "session-time-left" to "00:45:00",
                "trial" to "no"
            ),
            flags = mapOf("logged-in" to true, "trial" to false)
        )

        val source =
            "$(if logged-in == 'yes')" +
                "ONLINE-" +
                "$(if session-time-left != 0)" +
                "HAS-TIME" +
                "$(else)" +
                "NO-TIME" +
                "$(endif)" +
                "$(else)" +
                "OFFLINE" +
                "$(endif)"

        assertEquals("ONLINE-HAS-TIME", TemplateEngine.render(source, context))
    }

    @Test
    fun supportsElifNegationAndBooleanOperators() {
        val context = TemplateEngine.Context(
            values = mapOf("logged-in" to "no", "trial" to "yes", "error" to ""),
            flags = mapOf("logged-in" to false, "trial" to true, "error" to false)
        )

        val source =
            "$(if logged-in && !error)A$(elif trial == 'yes' || logged-in)B$(else)C$(endif)"

        assertEquals("B", TemplateEngine.render(source, context))
    }
}
