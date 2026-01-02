package app.aaps.pump.danarv2.services

import android.bluetooth.BluetoothSocket
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.SerialIOThread
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarv2.DanaRv2Plugin
import app.aaps.pump.danarv2.comm.MessageHashTableRv2
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import javax.inject.Provider

class DanaRv2ExecutionServiceTest : TestBaseWithProfile() {

    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var danaRv2Plugin: DanaRv2Plugin
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var messageHashTableRv2: MessageHashTableRv2
    @Mock lateinit var profile: Profile
    @Mock lateinit var pumpEnactResult: PumpEnactResult
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var danaRv2ExecutionService: DanaRv2ExecutionService

    @BeforeEach
    fun setup() {
        danaRv2ExecutionService = DanaRv2ExecutionService()
        danaRv2ExecutionService.aapsLogger = aapsLogger
        danaRv2ExecutionService.rxBus = rxBus
        danaRv2ExecutionService.preferences = preferences
        danaRv2ExecutionService.context = context
        danaRv2ExecutionService.rh = rh
        danaRv2ExecutionService.danaPump = danaPump
        danaRv2ExecutionService.fabricPrivacy = fabricPrivacy
        danaRv2ExecutionService.dateUtil = dateUtil
        danaRv2ExecutionService.aapsSchedulers = aapsSchedulers
        danaRv2ExecutionService.pumpSync = pumpSync
        danaRv2ExecutionService.activePlugin = activePlugin
        danaRv2ExecutionService.uiInteraction = uiInteraction
        danaRv2ExecutionService.pumpEnactResultProvider = pumpEnactResultProvider
        danaRv2ExecutionService.danaRKoreanPlugin = danaRKoreanPlugin
        danaRv2ExecutionService.danaRv2Plugin = danaRv2Plugin
        danaRv2ExecutionService.commandQueue = commandQueue
        danaRv2ExecutionService.messageHashTableRv2 = messageHashTableRv2
        danaRv2ExecutionService.profileFunction = profileFunction

        `when`(rh.gs(anyInt())).thenReturn("test")
        `when`(rh.gs(anyInt(), any())).thenReturn("test")
        `when`(activePlugin.activePump).thenReturn(danaRv2Plugin)
        `when`(danaRv2Plugin.pumpDescription).thenReturn(mockPumpDescription())
    }

    @Test
    fun testLoadEvents_notInitialized() {
        `when`(danaRv2Plugin.isInitialized()).thenReturn(false)

        val result = danaRv2ExecutionService.loadEvents()

        assertThat(result).isNotNull()
        assertThat(result.success).isFalse()
        assertThat(result.comment).contains("pump not initialized")
    }

    @Test
    fun testLoadEvents_notConnected() {
        `when`(danaRv2Plugin.isInitialized()).thenReturn(true)

        val result = danaRv2ExecutionService.loadEvents()

        assertThat(result).isNotNull()
        assertThat(result.success).isFalse()
    }

    @Test
    fun testTempBasal_notConnected() {
        val result = danaRv2ExecutionService.tempBasal(120, 1)

        assertThat(result).isFalse()
    }

    @Test
    fun testHighTempBasal_notConnected() {
        val result = danaRv2ExecutionService.highTempBasal(150, 15)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_notConnected() {
        val result = danaRv2ExecutionService.tempBasalShortDuration(150, 15)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration_invalidDuration() {
        val result = danaRv2ExecutionService.tempBasalShortDuration(150, 20)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalStop_notConnected() {
        val result = danaRv2ExecutionService.tempBasalStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolus_notConnected() {
        val result = danaRv2ExecutionService.extendedBolus(2.0, 2)

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolusStop_notConnected() {
        val result = danaRv2ExecutionService.extendedBolusStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testBolus_notConnected() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = 5.0
        val result = danaRv2ExecutionService.bolus(detailedBolusInfo)

        assertThat(result).isFalse()
    }

    @Test
    fun testUpdateBasalsInPump_notConnected() {
        `when`(profileFunction.getProfile()).thenReturn(profile)
        `when`(profile.getBasal()).thenReturn(1.0)

        val result = danaRv2ExecutionService.updateBasalsInPump(profile)

        assertThat(result).isFalse()
    }

    @Test
    fun testSetUserOptions_notConnected() {
        val result = danaRv2ExecutionService.setUserOptions()

        assertThat(result).isNotNull()
        assertThat(result.success).isFalse()
    }

    private fun mockPumpDescription(): app.aaps.core.data.pump.defs.PumpDescription {
        return app.aaps.core.data.pump.defs.PumpDescription().apply {
            basalStep = 0.01
        }
    }
}
