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

    /**
     * The three above all pass the arguments through the `vararg` overload, which always worked. A
     * `TextRef` can also carry its own arguments, and that path was ignored: only the overload
     * formatted. Most readers take a bare ref - the notification and constraint paths among them - so
     * a message built with `withArgs` reached the screen as its raw format string, "Limiting max
     * basal rate to %1$.2f U/h", and went to Nightscout that way too. Android has always read
     * `ref.args`, so this was iOS and desktop only.
     */
    @Test fun `substitutes arguments the ref carries itself`() {
        assertEquals("Hello Bob", resolver.gs(TextRef.Named("demo", "with_arg", listOf("Bob"))))
    }

    @Test fun `substitutes several arguments the ref carries itself`() {
        assertEquals("2 of 3", resolver.gs(TextRef.Named("demo", "two_args", listOf(2, 3))))
    }

    /** No template to substitute into, so the name is shown as-is - the same fallback Android uses. */
    @Test fun `an unresolved name carrying arguments still shows the name`() {
        assertEquals("unknown", resolver.gs(TextRef.Named("demo", "unknown", listOf("Bob"))))
    }

    /**
     * The overload rebuilds the ref rather than formatting the resolved text, so its arguments
     * replace any the ref already carried instead of being applied on top of an already-substituted
     * string.
     */
    @Test fun `explicit arguments replace the ones the ref carried`() {
        assertEquals("Hello Ann", resolver.gs(TextRef.Named("demo", "with_arg", listOf("Bob")), "Ann"))
    }

    @Test fun `the not localised lookup reads the same map`() {
        // The generated map is English, so there is nothing else it could read. Stated as a test so
        // that adding translations later has to decide what this means rather than inheriting it.
        assertEquals("Hello", resolver.gsNotLocalised(TextRef.Named("demo", "greeting")))
    }

    /**
     * The shell answers this, so both answers have to survive the constructor.
     *
     * It used to be hardcoded false, which meant an iPhone printed "3 days 4 hours" in the status
     * light row where an Android phone of the same width printed "3d4h". Deliberately not read from
     * the machine running the test - `isCompactScreen()` is what the shells pass, and a test that
     * called it would say something different on a simulator than on a desktop.
     */
    @Test fun `short text mode is off by default and on when the shell asks for it`() {
        assertEquals(false, resolver.shortTextMode())
        assertEquals(false, GeneratedTextResolver(compactScreen = false).shortTextMode())
        assertEquals(true, GeneratedTextResolver(compactScreen = true).shortTextMode())
    }
}
