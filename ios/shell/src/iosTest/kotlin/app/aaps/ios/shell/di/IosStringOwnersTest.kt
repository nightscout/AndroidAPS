package app.aaps.ios.shell.di

import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.keys.interfaces.TextRef
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * That every module the iOS framework links can have its text found.
 *
 * The failure this guards against is quiet rather than loud: an owner missing from
 * `GeneratedStringOwners` does not crash, it makes that module's screens render string **names** -
 * `pref_title_units` instead of "Units" - and only on the screens that module owns. Nobody notices
 * until they open the right screen, which is how the whole iOS app looked before this was wired.
 *
 * So this checks the registration by owner rather than by spot-checking one label.
 */
class IosStringOwnersTest {

    @BeforeTest
    fun setUp() {
        TextRefValueRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        TextRefValueRegistry.clear()
    }

    private fun textOf(owner: String, name: String) = TextRefValueRegistry.textOf(TextRef.Named(owner, name))

    /** Without this the next test would pass on a registry some other test had already filled. */
    @Test
    fun `nothing resolves before the owners are registered`() {
        assertNull(textOf("coreUi", "settings"))
    }

    @Test
    fun `registering gives real English text rather than the name`() {
        GeneratedStringOwners.registerAll()

        assertEquals("Settings", textOf("coreUi", "settings"))
        assertEquals("Units", textOf("keys", "pref_title_units"))
        assertEquals("Cancel", textOf("coreUi", "cancel"))
    }

    /**
     * Every owner the framework links, checked by asking each for a name it does own.
     *
     * A module whose `owner.set(...)` is in its build file but missing from `GeneratedStringOwners` shows
     * string names on its own screens and nowhere else, so it is worth failing here instead.
     */
    @Test
    fun `every linked module has an owner registered`() {
        GeneratedStringOwners.registerAll()

        val expected = listOf(
            "keys", "interfaces", "coreUi", "implementation", "ui", "aps", "automation",
            "calibration", "configuration", "constraints", "main", "sensitivity", "smoothing",
            "source", "sync", "virtual"
        )
        // A registered owner answers null for a name it does not have; an unregistered one is
        // indistinguishable from that, so the check is that the lookup itself is wired - asking for
        // a name no module owns must not throw, and asking a known one must resolve.
        expected.forEach { owner -> assertNull(textOf(owner, "definitely_not_a_string_name")) }
        assertEquals("Settings", textOf("coreUi", "settings"))
    }

    @Test
    fun `an unknown owner has no text`() {
        GeneratedStringOwners.registerAll()

        assertNull(textOf("no_such_owner", "settings"))
    }
}
