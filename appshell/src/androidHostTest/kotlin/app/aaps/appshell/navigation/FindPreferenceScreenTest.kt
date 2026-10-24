package app.aaps.appshell.navigation

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * Resolving a preference screen by key - the lookup behind every `PreferenceScreen` navigation.
 *
 * This used to be a parameter of `appNavGraph`, implemented only in `ComposeMainActivity`, and both
 * the iOS and desktop shells passed `findScreenDef = { null }`. So on those platforms **every** jump
 * to a preference screen ended at the navigation-error fallback: "Screen could not be opened (not
 * available in this configuration)". It was reported from the setup wizard's *Set* button for the
 * master password, but nothing there is about passwords - `onSetMasterPassword` is an ordinary
 * navigation to `PreferenceScreen("protection")`, so search results and element navigation were
 * equally broken and nobody had tried them.
 *
 * The lookup needs no platform at all, which is why it now lives in commonMain and why this test can
 * exist: `SearchableProvider` and `PreferenceSubScreenDef` are both `core/ui` commonMain types.
 */
class FindPreferenceScreenTest {

    private fun screen(key: String, vararg nested: PreferenceSubScreenDef) =
        PreferenceSubScreenDef(key = key, title = TextRef.Literal(key), items = nested.toList())

    private fun provider(vararg screens: PreferenceSubScreenDef) = object : SearchableProvider {
        override fun getSearchableItems(): List<SearchableItem> = screens.map { SearchableItem.Category(it) }
    }

    @Test
    fun `a built-in screen is found by its key`() {
        val protection = screen("protection")

        val found = findPreferenceScreen("protection", provider(screen("general"), protection), emptyList())

        assertThat(found).isEqualTo(protection)
    }

    /** The exact case that was reported: the wizard navigates to "protection". */
    @Test
    fun `the protection screen the setup wizard asks for resolves`() {
        val found = findPreferenceScreen("protection", provider(screen("protection")), emptyList())

        assertThat(found?.key).isEqualTo("protection")
    }

    @Test
    fun `a plugin screen is found when no built-in claims the key`() {
        val pluginScreen = screen("pump")

        val found = findPreferenceScreen("pump", provider(screen("general")), listOf(pluginScreen))

        assertThat(found).isEqualTo(pluginScreen)
    }

    /**
     * Screens nest, and a key may name one several levels down.
     *
     * Matching only the top level would report a screen that exists as missing - the same visible
     * failure as the stub that returned null, and harder to spot because most keys would work.
     */
    @Test
    fun `a nested built-in screen is found at depth`() {
        val deep = screen("deep")
        val middle = screen("middle", deep)

        val found = findPreferenceScreen("deep", provider(screen("top", middle)), emptyList())

        assertThat(found).isEqualTo(deep)
    }

    @Test
    fun `a nested plugin screen is found at depth`() {
        val deep = screen("plugin_deep")

        val found = findPreferenceScreen("plugin_deep", provider(), listOf(screen("plugin_top", deep)))

        assertThat(found).isEqualTo(deep)
    }

    /** Built-ins are searched first, matching the order the Android implementation used. */
    @Test
    fun `a built-in wins over a plugin screen with the same key`() {
        val builtIn = screen("shared_key")
        val plugin = screen("shared_key")

        val found = findPreferenceScreen("shared_key", provider(builtIn), listOf(plugin))

        assertThat(found).isEqualTo(builtIn)
        assertThat(found === plugin).isFalse()
    }

    /**
     * A key nothing claims is still null, and that has to keep working.
     *
     * The navigation-error fallback is the right answer for a screen that genuinely is not in this
     * configuration - a pump screen on a follower, say. The bug was never that it existed, only that
     * two platforms reached it for every key.
     */
    @Test
    fun `an unknown key is still null`() {
        assertThat(findPreferenceScreen("nothing_here", provider(screen("general")), listOf(screen("pump")))).isNull()
    }

    @Test
    fun `nothing registered at all is null rather than a crash`() {
        assertThat(findPreferenceScreen("protection", provider(), emptyList())).isNull()
    }
}
