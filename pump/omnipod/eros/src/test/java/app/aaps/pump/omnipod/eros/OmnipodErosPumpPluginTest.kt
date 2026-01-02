package app.aaps.pump.omnipod.eros

import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.determineCorrectBasalSize
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.implementation.pump.PumpEnactResultObject
import app.aaps.pump.common.defs.TempBasalPair
import app.aaps.pump.common.hw.rileylink.RileyLinkUtil
import app.aaps.pump.common.hw.rileylink.service.RileyLinkServiceData
import app.aaps.pump.omnipod.eros.driver.manager.ErosPodStateManager
import app.aaps.pump.omnipod.eros.history.database.ErosHistoryDatabase
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import app.aaps.pump.omnipod.eros.util.AapsOmnipodUtil
import app.aaps.pump.omnipod.eros.util.OmnipodAlertUtil
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.rx.TestAapsSchedulers
import com.google.common.truth.Truth.assertThat
import org.joda.time.DateTimeZone
import org.joda.time.tz.UTCProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OmnipodErosPumpPluginTest : TestBaseWithProfile() {

    @Mock lateinit var aapsOmnipodErosManager: AapsOmnipodErosManager
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var rileyLinkUtil: RileyLinkUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var erosHistoryDatabase: ErosHistoryDatabase
    @Mock lateinit var erosPodStateManager: ErosPodStateManager
    @Mock lateinit var rileyLinkServiceData: RileyLinkServiceData
    @Mock lateinit var aapsOmnipodUtil: AapsOmnipodUtil
    @Mock lateinit var omnipodAlertUtil: OmnipodAlertUtil

    @BeforeEach
    fun prepare() {
        whenever(rh.gs(ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong()))
            .thenReturn("")
    }

    @Test fun testSetTempBasalPercent() {
        DateTimeZone.setProvider(UTCProvider())

        // mock all the things
        val plugin = OmnipodErosPumpPlugin(
            aapsLogger, rh, preferences, commandQueue, TestAapsSchedulers(), rxBus, context,
            erosPodStateManager, aapsOmnipodErosManager, fabricPrivacy, rileyLinkServiceData, dateUtil, aapsOmnipodUtil,
            rileyLinkUtil, omnipodAlertUtil, profileFunction, pumpSync, uiInteraction, erosHistoryDatabase, decimalFormatter, pumpEnactResultProvider
        )
        val pumpState = PumpSync.PumpState(null, null, null, null, "")
        whenever(pumpSync.expectedPumpState()).thenReturn(pumpState)
        whenever(rileyLinkUtil.rileyLinkHistory).thenReturn(ArrayList())
        val profile: Profile = mock()

        // always return a PumpEnactResult containing same rate and duration as input
        whenever(
            aapsOmnipodErosManager.setTemporaryBasal(ArgumentMatchers.any(TempBasalPair::class.java))
        ).thenAnswer { invocation: InvocationOnMock ->
            val pair = invocation.getArgument<TempBasalPair>(0)
            val result = PumpEnactResultObject(rh)
            result.absolute(pair.insulinRate)
            result.duration(pair.durationMinutes)
            result
        }

        // Given standard basal
        whenever(profile.getBasal()).thenReturn(0.5)
        // When
        var result1 = plugin.setTempBasalPercent(80, 30, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        var result2 = plugin.setTempBasalPercent(5000, 30000, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        val result3 = plugin.setTempBasalPercent(0, 30, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        val result4 = plugin.setTempBasalPercent(0, 0, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        val result5 = plugin.setTempBasalPercent(-50, 60, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return correct values
        assertThat(result1.absolute).isWithin(0.01).of(0.4)
        assertThat(result1.duration).isEqualTo(30)
        assertThat(result2.absolute).isWithin(0.01).of(25.0)
        assertThat(result2.duration).isEqualTo(30000)
        assertThat(result3.absolute).isWithin(0.01).of(0.0)
        assertThat(result3.duration).isEqualTo(30)
        assertThat(result4.absolute).isWithin(0.01).of(-1.0)
        assertThat(result4.duration).isEqualTo(-1)
        // this is validated downstream, see TempBasalExtraCommand
        assertThat(result5.absolute).isWithin(0.01).of(-0.25)
        assertThat(result5.duration).isEqualTo(60)

        // Given zero basal
        whenever(profile.getBasal()).thenReturn(0.0)
        // When
        result1 = plugin.setTempBasalPercent(8000, 90, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        result2 = plugin.setTempBasalPercent(0, 0, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return zero values
        assertThat(result1.absolute).isWithin(0.01).of(0.0)
        assertThat(result1.duration).isEqualTo(90)
        assertThat(result2.absolute).isWithin(0.01).of(-1.0)
        assertThat(result2.duration).isEqualTo(-1)

        // Given unhealthy basal
        whenever(profile.getBasal()).thenReturn(500.0)
        // When treatment
        result1 =
            plugin.setTempBasalPercent(80, 30, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return sane values
        assertThat(result1.absolute).isWithin(0.01).of(PumpType.OMNIPOD_EROS.determineCorrectBasalSize(500.0 * 0.8))
        assertThat(result1.duration).isEqualTo(30)

        // Given weird basal
        whenever(profile.getBasal()).thenReturn(1.234567)
        // When treatment
        result1 = plugin.setTempBasalPercent(280, 600, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return sane values
        assertThat(result1.absolute).isWithin(0.01).of(3.4567876)
        assertThat(result1.duration).isEqualTo(600)

        // Given negative basal
        whenever(profile.getBasal()).thenReturn(-1.234567)
        // When treatment
        result1 = plugin.setTempBasalPercent(280, 510, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return negative value (this is validated further downstream, see TempBasalExtraCommand)
        assertThat(result1.absolute).isWithin(0.01).of(-3.4567876)
        assertThat(result1.duration).isEqualTo(510)
    }
}
