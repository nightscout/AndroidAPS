package app.aaps.ui.plugin

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Covers the shared switch-commit core (execute → ConfigBuilder, the hardware-pump gate) and the concurrency
 * guards, which neither ViewModel test drives. The blocking work runs on an injected test dispatcher so
 * [kotlinx.coroutines.test.advanceUntilIdle] can complete it in virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class PluginSwitchHandlerTest {

    private val activePlugin = mock<ActivePlugin>()
    private val configBuilder = mock<ConfigBuilder>()

    @Test
    fun `multi-select toggle commits immediately and refreshes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val plugin = mock<PluginBase>()
        whenever(configBuilder.requestPluginSwitch(plugin, true, PluginType.SYNC)).thenReturn(null)
        var switched = 0
        val sut = PluginSwitchHandler(CoroutineScope(dispatcher), activePlugin, configBuilder, onSwitched = { switched++ }, ioDispatcher = dispatcher)

        sut.toggle(plugin, PluginType.SYNC, true)
        advanceUntilIdle()

        verify(configBuilder).requestPluginSwitch(plugin, true, PluginType.SYNC)
        assertThat(switched).isEqualTo(1)
        assertThat(sut.dialogs.value.pluginSwitchConfirmation).isNull()
    }

    @Test
    fun `single-select enabling defers the switch behind the swap confirmation`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val current = mock<PluginBase>()
        val target = mock<PluginBase>()
        whenever(current.isEnabled(PluginType.APS)).thenReturn(true)
        whenever(current.name).thenReturn("Old")
        whenever(target.name).thenReturn("New")
        whenever(activePlugin.getSpecificPluginsList(PluginType.APS)).thenReturn(arrayListOf(current, target))
        whenever(configBuilder.requestPluginSwitch(target, true, PluginType.APS)).thenReturn(null)
        var switched = 0
        val sut = PluginSwitchHandler(CoroutineScope(dispatcher), activePlugin, configBuilder, onSwitched = { switched++ }, ioDispatcher = dispatcher)

        sut.toggle(target, PluginType.APS, true)
        advanceUntilIdle()

        assertThat(sut.dialogs.value.pluginSwitchConfirmation?.fromName).isEqualTo("Old")
        assertThat(sut.dialogs.value.pluginSwitchConfirmation?.toName).isEqualTo("New")
        verify(configBuilder, never()).requestPluginSwitch(target, true, PluginType.APS)

        sut.confirmSwitch()
        advanceUntilIdle()

        verify(configBuilder).requestPluginSwitch(target, true, PluginType.APS)
        assertThat(sut.dialogs.value.pluginSwitchConfirmation).isNull()
        assertThat(switched).isEqualTo(1)
    }

    @Test
    fun `hardware-pump warning raises the gate and confirm commits it`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val pump = mock<PluginBase>()
        whenever(pump.isEnabled(PluginType.PUMP)).thenReturn(false) // no active pump -> straight to execute
        whenever(activePlugin.getSpecificPluginsList(PluginType.PUMP)).thenReturn(arrayListOf(pump))
        whenever(configBuilder.requestPluginSwitch(pump, true, PluginType.PUMP)).thenReturn("Allow hardware pump?")
        var switched = 0
        val sut = PluginSwitchHandler(CoroutineScope(dispatcher), activePlugin, configBuilder, onSwitched = { switched++ }, ioDispatcher = dispatcher)

        sut.toggle(pump, PluginType.PUMP, true)
        advanceUntilIdle()

        assertThat(sut.dialogs.value.hardwarePumpConfirmation?.message).isEqualTo("Allow hardware pump?")
        verify(configBuilder, never()).confirmPumpPluginSwitch(pump, true, PluginType.PUMP)

        sut.confirmHardwarePump()
        advanceUntilIdle()

        verify(configBuilder).confirmPumpPluginSwitch(pump, true, PluginType.PUMP)
        assertThat(sut.dialogs.value.hardwarePumpConfirmation).isNull()
        assertThat(switched).isEqualTo(1)
    }

    @Test
    fun `double confirm fires the switch only once`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val current = mock<PluginBase>()
        val target = mock<PluginBase>()
        whenever(current.isEnabled(PluginType.APS)).thenReturn(true)
        whenever(current.name).thenReturn("Old")
        whenever(target.name).thenReturn("New")
        whenever(activePlugin.getSpecificPluginsList(PluginType.APS)).thenReturn(arrayListOf(current, target))
        whenever(configBuilder.requestPluginSwitch(target, true, PluginType.APS)).thenReturn(null)
        val sut = PluginSwitchHandler(CoroutineScope(dispatcher), activePlugin, configBuilder, onSwitched = { }, ioDispatcher = dispatcher)

        sut.toggle(target, PluginType.APS, true)
        sut.confirmSwitch()
        sut.confirmSwitch() // rapid double-tap of the confirm button

        advanceUntilIdle()

        verify(configBuilder, times(1)).requestPluginSwitch(target, true, PluginType.APS)
    }

    @Test
    fun `a second toggle is ignored while a switch is in flight`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val first = mock<PluginBase>()
        val second = mock<PluginBase>()
        whenever(configBuilder.requestPluginSwitch(first, true, PluginType.SYNC)).thenReturn(null)
        whenever(configBuilder.requestPluginSwitch(second, true, PluginType.SYNC)).thenReturn(null)
        val sut = PluginSwitchHandler(CoroutineScope(dispatcher), activePlugin, configBuilder, onSwitched = { }, ioDispatcher = dispatcher)

        sut.toggle(first, PluginType.SYNC, true)  // starts executing (busy), coroutine queued
        sut.toggle(second, PluginType.SYNC, true) // ignored while busy
        advanceUntilIdle()

        verify(configBuilder).requestPluginSwitch(first, true, PluginType.SYNC)
        verify(configBuilder, never()).requestPluginSwitch(second, true, PluginType.SYNC)
    }
}
