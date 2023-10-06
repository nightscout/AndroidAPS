package info.nightscout.androidaps.plugins.pump.omnipod.eros

import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.pump.defs.PumpType
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.implementation.utils.DecimalFormatterImpl
import app.aaps.shared.tests.TestBase
import app.aaps.shared.tests.rx.TestAapsSchedulers
import com.google.common.truth.Truth.assertThat
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import info.nightscout.pump.common.defs.TempBasalPair
import org.joda.time.DateTimeZone
import org.joda.time.tz.UTCProvider
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock

class OmnipodErosPumpPluginTest : TestBase() {

    @Mock lateinit var injector: HasAndroidInjector
    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var aapsOmnipodErosManager: AapsOmnipodErosManager
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var rileyLinkUtil: RileyLinkUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var erosHistoryDatabase: ErosHistoryDatabase

    private lateinit var decimalFormatter: DecimalFormatter

    @BeforeEach fun prepare() {
        `when`(rh.gs(ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong()))
            .thenReturn("")
        decimalFormatter = DecimalFormatterImpl(rh)
    }

    @Test fun testSetTempBasalPercent() {
        DateTimeZone.setProvider(UTCProvider())

        // mock all the things
        val plugin = OmnipodErosPumpPlugin(
            injector, aapsLogger, TestAapsSchedulers(), rxBus, null,
            rh, null, null, aapsOmnipodErosManager, commandQueue,
            null, null, null, null,
            rileyLinkUtil, null, null, pumpSync, uiInteraction, erosHistoryDatabase, decimalFormatter
        )
        val pumpState = PumpSync.PumpState(null, null, null, null, "")
        `when`(pumpSync.expectedPumpState()).thenReturn(pumpState)
        `when`(rileyLinkUtil.rileyLinkHistory).thenReturn(ArrayList())
        `when`(injector.androidInjector()).thenReturn(
            AndroidInjector { })
        val profile = Mockito.mock(
            Profile::class.java
        )

        // always return a PumpEnactResult containing same rate and duration as input
        `when`(
            aapsOmnipodErosManager.setTemporaryBasal(
                ArgumentMatchers.any(
                    TempBasalPair::class.java
                )
            )
        ).thenAnswer { invocation: InvocationOnMock ->
            val pair = invocation.getArgument<TempBasalPair>(0)
            val result = PumpEnactResult(injector)
            result.absolute(pair.insulinRate)
            result.duration(pair.durationMinutes)
            result
        }

        // Given standard basal
        `when`(profile.getBasal()).thenReturn(0.5)
        // When
        var result1 =
            plugin.setTempBasalPercent(80, 30, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        var result2 = plugin.setTempBasalPercent(
            5000,
            30000,
            profile,
            false,
            PumpSync.TemporaryBasalType.NORMAL
        )
        val result3 =
            plugin.setTempBasalPercent(0, 30, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        val result4 =
            plugin.setTempBasalPercent(0, 0, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        val result5 =
            plugin.setTempBasalPercent(-50, 60, profile, false, PumpSync.TemporaryBasalType.NORMAL)
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
        `when`(profile.getBasal()).thenReturn(0.0)
        // When
        result1 =
            plugin.setTempBasalPercent(8000, 90, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        result2 =
            plugin.setTempBasalPercent(0, 0, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return zero values
        assertThat(result1.absolute).isWithin(0.01).of(0.0)
        assertThat(result1.duration).isEqualTo(90)
        assertThat(result2.absolute).isWithin(0.01).of(-1.0)
        assertThat(result2.duration).isEqualTo(-1)

        // Given unhealthy basal
        `when`(profile.getBasal()).thenReturn(500.0)
        // When treatment
        result1 =
            plugin.setTempBasalPercent(80, 30, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return sane values
        assertThat(result1.absolute).isWithin(0.01).of(
            PumpType.OMNIPOD_EROS.determineCorrectBasalSize(500.0 * 0.8)
        )
        assertThat(result1.duration).isEqualTo(30)

        // Given weird basal
        `when`(profile.getBasal()).thenReturn(1.234567)
        // When treatment
        result1 =
            plugin.setTempBasalPercent(280, 600, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return sane values
        assertThat(result1.absolute).isWithin(0.01).of(3.4567876)
        assertThat(result1.duration).isEqualTo(600)

        // Given negative basal
        `when`(profile.getBasal()).thenReturn(-1.234567)
        // When treatment
        result1 =
            plugin.setTempBasalPercent(280, 510, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return negative value (this is validated further downstream, see TempBasalExtraCommand)
        assertThat(result1.absolute).isWithin(0.01).of(-3.4567876)
        assertThat(result1.duration).isEqualTo(510)
    }
}
