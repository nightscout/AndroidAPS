package app.aaps.implementation.resources

import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The resolver iOS and desktop read every label through.
 *
 * [TextRefValueRegistry] is a global, so each test clears it. Without that, a test that registered an
 * owner would decide the result of one that expects it to be missing, and the order tests run in
 * would change the outcome.
 */
class GeneratedTextResolverTest {

    private val resolver = GeneratedTextResolver()

    @BeforeTest fun register() {
        TextRefValueRegistry.clear()
        // The locale is ignored here: this test is about resolving and formatting, not translating.
        TextRefValueRegistry.register("demo") { name, _ ->
            when (name) {
                "greeting"   -> "Hello"
                "with_arg"   -> "Hello %1\$s"
                "two_args"   -> "%1\$s of %2\$s"
                else         -> null
            }
        }
    }

    @AfterTest fun cleanUp() {
        TextRefValueRegistry.clear()
    }

    @Test fun `resolves a registered name to its text`() {
        assertEquals("Hello", resolver.gs(TextRef.Named("demo", "greeting")))
    }

    @Test fun `falls back to the name when the owner is not registered`() {
        // What a shell that has not listed a module it depends on will show. Visibly wrong on
        // screen, which is the point - it names the module to add.
        assertEquals("greeting", resolver.gs(TextRef.Named("other", "greeting")))
    }

    @Test fun `falls back to the name when the owner has no such string`() {
        assertEquals("unknown", resolver.gs(TextRef.Named("demo", "unknown")))
    }

    @Test fun `a literal is its own text`() {
        assertEquals("as written", resolver.gs(TextRef.Literal("as written")))
    }

    @Test fun `an android resource id cannot be resolved and says so`() {
        // Shared code should never build one of these. Rendering the id makes that visible rather
        // than showing an empty label.
        assertEquals("res:42", resolver.gs(TextRef.AndroidRes(42)))
    }

    @Test fun `substitutes arguments into a registered format string`() {
        assertEquals("Hello Bob", resolver.gs(TextRef.Named("demo", "with_arg"), "Bob"))
    }

    @Test fun `substitutes several arguments`() {
        assertEquals("2 of 3", resolver.gs(TextRef.Named("demo", "two_args"), 2, 3))
    }

    @Test fun `an unresolved name with arguments still shows the name`() {
        assertEquals("unknown", resolver.gs(TextRef.Named("demo", "unknown"), "Bob"))
    }

    @Test fun `the not localised lookup reads the same map`() {
        // The generated map is English, so there is nothing else it could read. Stated as a test so
        // that adding translations later has to decide what this means rather than inheriting it.
        assertEquals("Hello", resolver.gsNotLocalised(TextRef.Named("demo", "greeting")))
    }

    @Test fun `short text mode is off`() {
        assertEquals(false, resolver.shortTextMode())
    }
}
