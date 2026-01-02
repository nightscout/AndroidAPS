package app.aaps.pump.danaR.services

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danar.comm.MessageHashTableR
import app.aaps.pump.danar.services.DanaRExecutionService
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRExecutionServiceTest : TestBaseWithProfile() {

    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var messageHashTableR: MessageHashTableR
    @Mock lateinit var profile: Profile
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var danaRExecutionService: DanaRExecutionService

    @BeforeEach
    fun setup() {
        danaRExecutionService = DanaRExecutionService()
        danaRExecutionService.aapsLogger = aapsLogger
        danaRExecutionService.rxBus = rxBus
        danaRExecutionService.preferences = preferences
        danaRExecutionService.context = context
        danaRExecutionService.rh = rh
        danaRExecutionService.danaPump = danaPump
        danaRExecutionService.fabricPrivacy = fabricPrivacy
        danaRExecutionService.dateUtil = dateUtil
        danaRExecutionService.aapsSchedulers = aapsSchedulers
        danaRExecutionService.pumpSync = pumpSync
        danaRExecutionService.activePlugin = activePlugin
        danaRExecutionService.uiInteraction = uiInteraction
        danaRExecutionService.pumpEnactResultProvider = pumpEnactResultProvider
        danaRExecutionService.danaRPlugin = danaRPlugin
        danaRExecutionService.danaRKoreanPlugin = danaRKoreanPlugin
        danaRExecutionService.commandQueue = commandQueue
        danaRExecutionService.messageHashTableR = messageHashTableR
        danaRExecutionService.profileFunction = profileFunction

        `when`(rh.gs(anyInt())).thenReturn("test")
        `when`(rh.gs(anyInt(), any())).thenReturn("test")
        `when`(danaRPlugin.pumpDescription).thenReturn(mockPumpDescription())
    }

    @Test
    fun testTempBasal_notConnected() {
        val result = danaRExecutionService.tempBasal(120, 1)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalStop_notConnected() {
        val result = danaRExecutionService.tempBasalStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolus_notConnected() {
        val result = danaRExecutionService.extendedBolus(2.0, 2)

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolusStop_notConnected() {
        val result = danaRExecutionService.extendedBolusStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testBolus_notConnected() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = 5.0
        val result = danaRExecutionService.bolus(detailedBolusInfo)

        assertThat(result).isFalse()
    }

    @Test
    fun testHighTempBasal() {
        val result = danaRExecutionService.highTempBasal(150, 30)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration() {
        val result = danaRExecutionService.tempBasalShortDuration(150, 15)

        assertThat(result).isFalse()
    }

    @Test
    fun testUpdateBasalsInPump_notConnected() {
        `when`(profileFunction.getProfile()).thenReturn(profile)
        `when`(profile.getBasal()).thenReturn(1.0)

        val result = danaRExecutionService.updateBasalsInPump(profile)

        assertThat(result).isFalse()
    }

    @Test
    fun testSetUserOptions_notConnected() {
        val result = danaRExecutionService.setUserOptions()

        assertThat(result).isNotNull()
        assertThat(result.success).isFalse()
    }

    private fun mockPumpDescription(): app.aaps.core.data.pump.defs.PumpDescription {
        return app.aaps.core.data.pump.defs.PumpDescription().apply {
            basalStep = 0.01
        }
    }
}
