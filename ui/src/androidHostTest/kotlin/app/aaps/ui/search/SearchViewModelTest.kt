package app.aaps.ui.search

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
internal class SearchViewModelTest {

    @Mock private lateinit var searchIndexBuilder: SearchIndexBuilder
    @Mock private lateinit var wikiSearchRepository: WikiSearchRepository
    @Mock private lateinit var nsClient: NsClient
    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var configBuilder: ConfigBuilder
    @Mock private lateinit var config: Config
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: SearchViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // StandardTestDispatcher defers the init{} launchIn coroutines (debounced query observer, the
        // masterOrPairedClientFlow observer, and the activeSelectionChanges observer) so construction stays clean
        // and we exercise the synchronous setter methods against the default uiState. The observed flows must
        // still be non-null.
        Dispatchers.setMain(StandardTestDispatcher())
        whenever(nsClient.masterOrPairedClientFlow).thenReturn(MutableStateFlow(false))
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(true))
        whenever(configBuilder.activeSelectionChanges).thenReturn(emptyFlow())
        whenever(preferences.get(StringNonKey.SearchHistory)).thenReturn("[]")
        sut = SearchViewModel(searchIndexBuilder, wikiSearchRepository, nsClient, activePlugin, configBuilder, config, preferences)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `history is loaded from the preferences on start`() {
        whenever(preferences.get(StringNonKey.SearchHistory)).thenReturn("""["temp target","basal"]""")

        val vm = SearchViewModel(searchIndexBuilder, wikiSearchRepository, nsClient, activePlugin, configBuilder, config, preferences)

        assertThat(vm.uiState.value.history).containsExactly("temp target", "basal").inOrder()
    }

    @Test
    fun `a damaged history value starts an empty list`() {
        whenever(preferences.get(StringNonKey.SearchHistory)).thenReturn("not json")

        val vm = SearchViewModel(searchIndexBuilder, wikiSearchRepository, nsClient, activePlugin, configBuilder, config, preferences)

        assertThat(vm.uiState.value.history).isEmpty()
    }

    @Test
    fun `recordCurrentQuery puts the trimmed query first and saves it`() {
        sut.onQueryChanged(" basal ")

        sut.recordCurrentQuery()

        assertThat(sut.uiState.value.history).containsExactly("basal")
        verify(preferences).put(StringNonKey.SearchHistory, """["basal"]""")
    }

    @Test
    fun `recordCurrentQuery ignores a blank query`() {
        sut.onQueryChanged("   ")

        sut.recordCurrentQuery()

        assertThat(sut.uiState.value.history).isEmpty()
        verify(preferences, never()).put(eq(StringNonKey.SearchHistory), any())
    }

    @Test
    fun `a repeated search moves to the top instead of appearing twice`() {
        sut.onQueryChanged("basal")
        sut.recordCurrentQuery()
        sut.onQueryChanged("insulin")
        sut.recordCurrentQuery()
        sut.onQueryChanged("Basal")
        sut.recordCurrentQuery()

        assertThat(sut.uiState.value.history).containsExactly("Basal", "insulin").inOrder()
    }

    @Test
    fun `the history keeps only the newest MAX_HISTORY searches`() {
        repeat(SearchViewModel.MAX_HISTORY + 3) { i ->
            sut.onQueryChanged("search $i")
            sut.recordCurrentQuery()
        }

        val history = sut.uiState.value.history
        assertThat(history).hasSize(SearchViewModel.MAX_HISTORY)
        assertThat(history.first()).isEqualTo("search ${SearchViewModel.MAX_HISTORY + 2}")
        assertThat(history).doesNotContain("search 0")
    }

    @Test
    fun `removeFromHistory removes only that entry and saves the rest`() {
        sut.onQueryChanged("basal")
        sut.recordCurrentQuery()
        sut.onQueryChanged("insulin")
        sut.recordCurrentQuery()

        sut.removeFromHistory("basal")

        assertThat(sut.uiState.value.history).containsExactly("insulin")
        verify(preferences).put(StringNonKey.SearchHistory, """["insulin"]""")
    }

    @Test
    fun `leaving search mode keeps the history`() {
        sut.onQueryChanged("basal")
        sut.recordCurrentQuery()

        sut.onSearchModeDeactivated()
        sut.onSearchModeActivated()

        assertThat(sut.uiState.value.history).containsExactly("basal")
    }

    @Test
    fun `default uiState is inactive and empty`() {
        val state = sut.uiState.value
        assertThat(state.isSearchActive).isFalse()
        assertThat(state.query).isEqualTo("")
        assertThat(state.results).isEmpty()
        assertThat(state.wikiResults).isEmpty()
        assertThat(state.isSearching).isFalse()
    }

    @Test
    fun `onQueryChanged updates the query`() {
        sut.onQueryChanged("insulin")

        assertThat(sut.uiState.value.query).isEqualTo("insulin")
    }

    @Test
    fun `onSearchModeActivated activates search and clears state`() {
        sut.onQueryChanged("stale")
        sut.onSearchModeActivated()

        val state = sut.uiState.value
        assertThat(state.isSearchActive).isTrue()
        assertThat(state.query).isEqualTo("")
        assertThat(state.results).isEmpty()
        assertThat(state.wikiResults).isEmpty()
    }

    @Test
    fun `onSearchModeDeactivated deactivates search and clears state`() {
        sut.onSearchModeActivated()
        sut.onQueryChanged("something")
        sut.onSearchModeDeactivated()

        val state = sut.uiState.value
        assertThat(state.isSearchActive).isFalse()
        assertThat(state.query).isEqualTo("")
        assertThat(state.results).isEmpty()
    }

    @Test
    fun `clearQuery empties the query but keeps search active`() {
        sut.onSearchModeActivated()
        sut.onQueryChanged("temp target")
        sut.clearQuery()

        val state = sut.uiState.value
        assertThat(state.query).isEqualTo("")
        assertThat(state.results).isEmpty()
        assertThat(state.isSearchActive).isTrue()
    }

    @Test
    fun `togglePlugin on a single-select plugin raises the swap confirmation`() {
        val current = mock<PluginBase>()
        whenever(current.isEnabled(PluginType.PUMP)).thenReturn(true)
        whenever(current.name).thenReturn("Old Pump")
        val target = mock<PluginBase>()
        whenever(target.getType()).thenReturn(PluginType.PUMP)
        whenever(target.isEnabled()).thenReturn(false)
        whenever(target.name).thenReturn("New Pump")
        whenever(activePlugin.getSpecificPluginsList(PluginType.PUMP)).thenReturn(arrayListOf(current, target))

        sut.togglePlugin(target)

        val confirmation = sut.uiState.value.pluginSwitchConfirmation
        assertThat(confirmation).isNotNull()
        assertThat(confirmation!!.fromName).isEqualTo("Old Pump")
        assertThat(confirmation.toName).isEqualTo("New Pump")
        verify(configBuilder, never()).requestPluginSwitch(any(), any(), any())
    }

    @Test
    fun `togglePlugin on a multi-select plugin raises no confirmation`() {
        val target = mock<PluginBase>()
        whenever(target.getType()).thenReturn(PluginType.SYNC)
        whenever(target.isEnabled()).thenReturn(false)

        sut.togglePlugin(target)

        assertThat(sut.uiState.value.pluginSwitchConfirmation).isNull()
    }

    @Test
    fun `dismissPluginSwitch clears the confirmation`() {
        val current = mock<PluginBase>()
        whenever(current.isEnabled(PluginType.PUMP)).thenReturn(true)
        whenever(current.name).thenReturn("Old Pump")
        val target = mock<PluginBase>()
        whenever(target.getType()).thenReturn(PluginType.PUMP)
        whenever(target.isEnabled()).thenReturn(false)
        whenever(target.name).thenReturn("New Pump")
        whenever(activePlugin.getSpecificPluginsList(PluginType.PUMP)).thenReturn(arrayListOf(current, target))
        sut.togglePlugin(target)
        assertThat(sut.uiState.value.pluginSwitchConfirmation).isNotNull()

        sut.dismissPluginSwitch()

        assertThat(sut.uiState.value.pluginSwitchConfirmation).isNull()
    }

    @Test
    fun `togglePlugin is blocked for a synced selection on an offline client`() {
        whenever(config.AAPSCLIENT).thenReturn(true)
        whenever(nsClient.masterReachable).thenReturn(MutableStateFlow(false))
        val target = mock<PluginBase>()
        whenever(target.getType()).thenReturn(PluginType.APS) // selectionSyncs = true

        sut.togglePlugin(target)

        assertThat(sut.uiState.value.pluginSwitchConfirmation).isNull()
        verify(activePlugin, never()).getSpecificPluginsList(any())
    }
}
