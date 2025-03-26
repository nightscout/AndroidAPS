package app.aaps.pump.equil

import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito

class EquilPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var equilManager: EquilManager

    private lateinit var equilPumpPlugin: EquilPumpPlugin

    @BeforeEach
    fun prepareMocks() {

        Mockito.`when`(rh.gs(anyInt())).thenReturn("")
        equilPumpPlugin =
            EquilPumpPlugin(
                aapsLogger, rh, preferences, commandQueue, aapsSchedulers, rxBus, context,
                fabricPrivacy, dateUtil, pumpSync, equilManager, decimalFormatter, instantiator
            )
    }

    @Test
    fun addPreferenceScreen() {
        val screen = preferenceManager.createPreferenceScreen(context)
        equilPumpPlugin.addPreferenceScreen(preferenceManager, screen, context, null)
        assertThat(screen.preferenceCount).isGreaterThan(0)
    }
}