package app.aaps.ui.search

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.sync.NsClient
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
        sut = SearchViewModel(searchIndexBuilder, wikiSearchRepository, nsClient, activePlugin, configBuilder, config)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

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
