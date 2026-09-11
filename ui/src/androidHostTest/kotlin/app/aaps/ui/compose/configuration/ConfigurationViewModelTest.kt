package app.aaps.ui.compose.configuration

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.keys.interfaces.Preferences
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
internal class ConfigurationViewModelTest {

    @Mock private lateinit var activePlugin: ActivePlugin
    @Mock private lateinit var configBuilder: ConfigBuilder
    @Mock private lateinit var config: Config
    @Mock private lateinit var preferences: Preferences

    private lateinit var sut: ConfigurationViewModel

    @BeforeEach
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        // init's two viewModelScope.launch bodies (loadCategories + activeSelectionChanges.collect) are deferred
        // by StandardTestDispatcher, so construction touches none of the mocks.
        Dispatchers.setMain(StandardTestDispatcher())
        sut = ConfigurationViewModel(activePlugin, configBuilder, config, preferences)
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `default uiState is simple mode with no categories and no confirmation`() {
        val state = sut.uiState.value
        assertThat(state.isSimpleMode).isTrue()
        assertThat(state.categories).isEmpty()
        assertThat(state.hardwarePumpConfirmation).isNull()
        assertThat(state.pluginSwitchConfirmation).isNull()
    }

    @Test
    fun `dismissHardwarePumpDialog clears confirmation and refreshes categories synchronously`() {
        // refreshCategories() reads preferences.simpleMode (false by default) and builds categories from
        // activePlugin; no visible plugins -> empty categories.
        whenever(activePlugin.getSpecificPluginsVisibleInList(any())).thenReturn(ArrayList<PluginBase>())

        sut.dismissHardwarePumpDialog()

        val state = sut.uiState.value
        assertThat(state.hardwarePumpConfirmation).isNull()
        assertThat(state.categories).isEmpty()
        assertThat(state.isSimpleMode).isFalse()
    }

    /** Builds a category holding [target] (the disabled plugin the user taps) plus an optional currently-[active]
     *  plugin of the same type, and populates the ViewModel's plugin lookup synchronously (dismissHardwarePumpDialog
     *  triggers a synchronous refreshCategories), then returns the built id of [target]. */
    private fun seedCategoryAndGetId(type: PluginType, target: PluginBase, active: PluginBase? = null): String {
        whenever(activePlugin.getSpecificPluginsVisibleInList(any())).thenReturn(ArrayList<PluginBase>())
        whenever(activePlugin.getSpecificPluginsVisibleInList(type)).thenReturn(arrayListOf(target))
        whenever(activePlugin.getSpecificPluginsList(type)).thenReturn(ArrayList(listOfNotNull(active, target)))
        sut.dismissHardwarePumpDialog()
        return sut.uiState.value.categories.first { it.type == type }.plugins.first().id
    }

    private fun activePump(name: String): PluginBase = mock<PluginBase>().also {
        whenever(it.isEnabled(PluginType.PUMP)).thenReturn(true)
        whenever(it.name).thenReturn(name)
    }

    @Test
    fun `enabling a single-select plugin opens the swap confirmation and defers the switch`() {
        val target = mock<PluginBase>()
        whenever(target.pluginDescription).thenReturn(PluginDescription())
        whenever(target.name).thenReturn("Omnipod")
        val id = seedCategoryAndGetId(PluginType.PUMP, target, active = activePump("Virtual pump"))

        sut.togglePluginEnabled(id, PluginType.PUMP, true)

        val confirmation = sut.uiState.value.pluginSwitchConfirmation
        assertThat(confirmation).isNotNull()
        assertThat(confirmation!!.fromName).isEqualTo("Virtual pump")
        assertThat(confirmation.toName).isEqualTo("Omnipod")
        // Nothing is switched until the user confirms.
        verify(configBuilder, never()).requestPluginSwitch(any(), any(), any())
    }

    @Test
    fun `dismissPluginSwitchDialog clears the pending swap confirmation`() {
        val target = mock<PluginBase>()
        whenever(target.pluginDescription).thenReturn(PluginDescription())
        whenever(target.name).thenReturn("Omnipod")
        val id = seedCategoryAndGetId(PluginType.PUMP, target, active = activePump("Virtual pump"))
        sut.togglePluginEnabled(id, PluginType.PUMP, true)
        assertThat(sut.uiState.value.pluginSwitchConfirmation).isNotNull()

        sut.dismissPluginSwitchDialog()

        assertThat(sut.uiState.value.pluginSwitchConfirmation).isNull()
        verify(configBuilder, never()).requestPluginSwitch(any(), any(), any())
    }

    @Test
    fun `toggling a multi-select plugin does not open the swap confirmation`() {
        val target = mock<PluginBase>()
        whenever(target.pluginDescription).thenReturn(PluginDescription())
        whenever(target.name).thenReturn("Wear")
        val id = seedCategoryAndGetId(PluginType.GENERAL, target)

        sut.togglePluginEnabled(id, PluginType.GENERAL, true)

        assertThat(sut.uiState.value.pluginSwitchConfirmation).isNull()
    }
}
