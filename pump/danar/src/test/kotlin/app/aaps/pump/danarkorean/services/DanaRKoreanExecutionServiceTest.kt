package app.aaps.pump.danarkorean.services

import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.pump.dana.DanaPump
import app.aaps.pump.danar.DanaRPlugin
import app.aaps.pump.danarkorean.DanaRKoreanPlugin
import app.aaps.pump.danarkorean.comm.MessageHashTableRKorean
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.Mockito.`when`

class DanaRKoreanExecutionServiceTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var danaRPlugin: DanaRPlugin
    @Mock lateinit var danaRKoreanPlugin: DanaRKoreanPlugin
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var messageHashTableRKorean: MessageHashTableRKorean
    @Mock lateinit var profile: Profile
    @Mock lateinit var danaPump: DanaPump
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var uiInteraction: UiInteraction

    private lateinit var danaRKoreanExecutionService: DanaRKoreanExecutionService

    @BeforeEach
    fun setup() {
        danaRKoreanExecutionService = DanaRKoreanExecutionService()
        danaRKoreanExecutionService.aapsLogger = aapsLogger
        danaRKoreanExecutionService.rxBus = rxBus
        danaRKoreanExecutionService.preferences = preferences
        danaRKoreanExecutionService.context = context
        danaRKoreanExecutionService.rh = rh
        danaRKoreanExecutionService.danaPump = danaPump
        danaRKoreanExecutionService.fabricPrivacy = fabricPrivacy
        danaRKoreanExecutionService.dateUtil = dateUtil
        danaRKoreanExecutionService.aapsSchedulers = aapsSchedulers
        danaRKoreanExecutionService.pumpSync = pumpSync
        danaRKoreanExecutionService.activePlugin = activePlugin
        danaRKoreanExecutionService.uiInteraction = uiInteraction
        danaRKoreanExecutionService.pumpEnactResultProvider = pumpEnactResultProvider
        danaRKoreanExecutionService.constraintChecker = constraintChecker
        danaRKoreanExecutionService.danaRPlugin = danaRPlugin
        danaRKoreanExecutionService.danaRKoreanPlugin = danaRKoreanPlugin
        danaRKoreanExecutionService.commandQueue = commandQueue
        danaRKoreanExecutionService.messageHashTableRKorean = messageHashTableRKorean
        danaRKoreanExecutionService.profileFunction = profileFunction

        `when`(rh.gs(anyInt())).thenReturn("test")
        `when`(rh.gs(anyInt(), any())).thenReturn("test")
        `when`(danaRKoreanPlugin.pumpDescription).thenReturn(mockPumpDescription())
    }

    @Test
    fun testLoadEvents() {
        val result = danaRKoreanExecutionService.loadEvents()

        assertThat(result).isNull()
    }

    @Test
    fun testSetUserOptions() {
        val result = danaRKoreanExecutionService.setUserOptions()

        assertThat(result).isNull()
    }

    @Test
    fun testTempBasal_notConnected() {
        val result = danaRKoreanExecutionService.tempBasal(120, 1)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalStop_notConnected() {
        val result = danaRKoreanExecutionService.tempBasalStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolus_notConnected() {
        val result = danaRKoreanExecutionService.extendedBolus(2.0, 2)

        assertThat(result).isFalse()
    }

    @Test
    fun testExtendedBolusStop_notConnected() {
        val result = danaRKoreanExecutionService.extendedBolusStop()

        assertThat(result).isFalse()
    }

    @Test
    fun testBolus_notConnected() {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = 5.0
        val result = danaRKoreanExecutionService.bolus(detailedBolusInfo)

        assertThat(result).isFalse()
    }

    @Test
    fun testHighTempBasal() {
        val result = danaRKoreanExecutionService.highTempBasal(150, 30)

        assertThat(result).isFalse()
    }

    @Test
    fun testTempBasalShortDuration() {
        val result = danaRKoreanExecutionService.tempBasalShortDuration(150, 15)

        assertThat(result).isFalse()
    }

    @Test
    fun testUpdateBasalsInPump_notConnected() {
        `when`(profileFunction.getProfile()).thenReturn(profile)
        `when`(profile.getBasal()).thenReturn(1.0)

        val result = danaRKoreanExecutionService.updateBasalsInPump(profile)

        assertThat(result).isFalse()
    }

    private fun mockPumpDescription(): app.aaps.core.data.pump.defs.PumpDescription {
        return app.aaps.core.data.pump.defs.PumpDescription().apply {
            basalStep = 0.01
        }
    }
}
