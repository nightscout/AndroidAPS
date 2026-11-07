package app.aaps.ui.search

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.AppPlatform
import app.aaps.core.keys.interfaces.PreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.VisibilityContext
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import app.aaps.shared.tests.generatedTextResolver
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Search must not find a setting that its preference screen hides. The screen honours each key's
 * `visibility`; search ignored it, so on a patch pump it still offered the battery age and insulin
 * age thresholds and the Prime/Fill presets, which have no effect there.
 */
internal class SearchIndexBuilderTest {

    private val activePlugin: ActivePlugin = mock()
    private val preferences: Preferences = mock()
    private val config: Config = mock()
    private val visibilityContext: VisibilityContext = mock()

    private val fillButtons = listOf(DoubleKey.ActionsFillButton1, DoubleKey.ActionsFillButton2, DoubleKey.ActionsFillButton3)

    /** Same shape as the Prime/Fill settings screen the Fill dialog opens. */
    private val fillScreen = PreferenceSubScreenDef(key = "prime_fill_settings", title = CoreUiStrings.prime_fill, items = fillButtons)
    private val fillProvider = object : SearchableProvider {
        override fun getSearchableItems(): List<SearchableItem> = listOf(SearchableItem.Category(fillScreen))
    }

    @BeforeEach
    fun setUp() {
        whenever(activePlugin.getPluginsList()).thenReturn(arrayListOf())
        whenever(config.platform).thenReturn(AppPlatform.Android)
        whenever(preferences.getAllPreferenceKeys()).thenReturn(
            listOf(IntKey.OverviewBageWarning, IntKey.OverviewIageWarning, IntKey.OverviewSageWarning)
        )
    }

    private fun index(providers: Set<SearchableProvider> = emptySet()): List<SearchIndexEntry> =
        SearchIndexBuilder(activePlugin, providers, preferences, config, generatedTextResolver(), visibilityContext).getIndex()

    private fun indexedKeys(): List<PreferenceKey> =
        index().mapNotNull { (it.item as? SearchableItem.Preference)?.preferenceKey }

    @Test
    fun `patch pump without a replaceable battery hides battery age and insulin age`() {
        whenever(visibilityContext.isPatchPump).thenReturn(true)
        whenever(visibilityContext.isBatteryReplaceable).thenReturn(false)
        whenever(visibilityContext.isBatteryChangeLoggingEnabled).thenReturn(false)

        assertThat(indexedKeys()).containsExactly(IntKey.OverviewSageWarning)
    }

    @Test
    fun `tube pump with a replaceable battery finds all three`() {
        whenever(visibilityContext.isPatchPump).thenReturn(false)
        whenever(visibilityContext.isBatteryReplaceable).thenReturn(true)

        assertThat(indexedKeys()).containsExactly(IntKey.OverviewBageWarning, IntKey.OverviewIageWarning, IntKey.OverviewSageWarning)
    }

    // Omnipod Eros: the pod has no battery, but the user can log RileyLink battery changes.
    @Test
    fun `battery change logging keeps battery age`() {
        whenever(visibilityContext.isPatchPump).thenReturn(true)
        whenever(visibilityContext.isBatteryReplaceable).thenReturn(false)
        whenever(visibilityContext.isBatteryChangeLoggingEnabled).thenReturn(true)

        assertThat(indexedKeys()).containsExactly(IntKey.OverviewBageWarning, IntKey.OverviewSageWarning)
    }

    @Test
    fun `a pump that primes itself hides the Prime-Fill presets and their screen`() {
        whenever(preferences.getAllPreferenceKeys()).thenReturn(fillButtons)
        whenever(visibilityContext.isRefillingCapable).thenReturn(false)

        val index = index(setOf(fillProvider))

        assertThat(index.filter { it.item is SearchableItem.Preference }).isEmpty()
        assertThat(index.map { it.item.key }).doesNotContain(fillScreen.key)
    }

    @Test
    fun `a tube pump finds the Prime-Fill presets and their screen`() {
        whenever(preferences.getAllPreferenceKeys()).thenReturn(fillButtons)
        whenever(visibilityContext.isRefillingCapable).thenReturn(true)

        val index = index(setOf(fillProvider))

        assertThat(index.mapNotNull { (it.item as? SearchableItem.Preference)?.preferenceKey }).containsExactlyElementsIn(fillButtons)
        assertThat(index.map { it.item.key }).contains(fillScreen.key)
    }
}
