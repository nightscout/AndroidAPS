package app.aaps.desktop.shell.di

import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.KeysStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.CoreUiStrings
import app.aaps.implementation.ImplementationStrings
import app.aaps.implementation.resources.GeneratedTextResolver
import app.aaps.ui.UiStrings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * That every owner the desktop shell registers is spelled the way its module spells it.
 *
 * This is the one part of the string wiring a compiler cannot check. The owner is a plain String on
 * both sides - `owner.set("coreUi")` in the module's build file, `register("coreUi")` in
 * [DesktopStringOwners] - so a typo compiles, and the only symptom is a screen showing string
 * **names** instead of words. That reads like unfinished UI rather than like a bug, which is how it
 * would survive review.
 *
 * Each case takes its owner from that module's own generated object instead of repeating the
 * literal, so a module that renames its owner fails here rather than going quiet.
 */
class DesktopStringOwnersTest {

    private val resolver = GeneratedTextResolver()

    @BeforeTest fun register() {
        TextRefValueRegistry.clear()
        DesktopStringOwners.registerAll()
    }

    @AfterTest fun cleanUp() {
        TextRefValueRegistry.clear()
    }

    @Test fun `resolves a string to real english text and not to its name`() {
        assertEquals("General", resolver.gs(CoreUiStrings.configbuilder_general))
    }

    @Test fun `every registered owner resolves one of its own strings`() {
        // One case per registration, so a single misspelled owner fails rather than only the first.
        listOf(
            CoreUiStrings.configbuilder_general,
            KeysStrings.absorption_cutoff_summary,
            UiStrings.a11y_add_new_insulin,
            InterfacesStrings.advisoralarm,
            ImplementationStrings.backup_to_google_drive
        ).forEach { ref ->
            val named = ref as TextRef.Named
            assertNotEquals(
                named.name,
                resolver.gs(named),
                "owner '${named.owner}' resolves to the string name, so it is not registered correctly"
            )
        }
    }

    @Test fun `substitutes an argument into a real format string`() {
        // Proves the generated text keeps its placeholder through XML parsing and Kotlin escaping.
        // "Run alarm in %1$d min" is what this one says in strings.xml.
        assertEquals("Run alarm in 20 min", resolver.gs(InterfacesStrings.alarminxmin, 20))
    }

    @Test fun `an owner the shell does not depend on falls back to the name`() {
        // The other half of the contract: an unregistered module must not resolve and must not throw.
        assertEquals("greeting", resolver.gs(TextRef.Named("no_such_module", "greeting")))
    }
}
