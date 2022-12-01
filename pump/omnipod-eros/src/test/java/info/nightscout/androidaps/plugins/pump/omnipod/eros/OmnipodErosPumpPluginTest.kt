package info.nightscout.androidaps.plugins.pump.omnipod.eros

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.pump.common.hw.rileylink.RileyLinkUtil
import info.nightscout.androidaps.plugins.pump.omnipod.eros.history.database.ErosHistoryDatabase
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.pump.common.defs.TempBasalPair
import info.nightscout.rx.TestAapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.interfaces.ResourceHelper
import org.joda.time.DateTimeZone
import org.joda.time.tz.UTCProvider
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock

class OmnipodErosPumpPluginTest : TestBase() {

    @Mock lateinit var injector: HasAndroidInjector
    @Mock lateinit var rh: ResourceHelper
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) lateinit var activePlugin: ActivePlugin
    @Mock lateinit var aapsOmnipodErosManager: AapsOmnipodErosManager
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var commandQueue: CommandQueue
    @Mock lateinit var rileyLinkUtil: RileyLinkUtil
    @Mock lateinit var pumpSync: PumpSync
    @Mock lateinit var erosHistoryDatabase: ErosHistoryDatabase

    private var rxBusWrapper = RxBus(TestAapsSchedulers(), aapsLogger)

    @BeforeEach fun prepare() {
        `when`(rh.gs(ArgumentMatchers.anyInt(), ArgumentMatchers.anyLong()))
            .thenReturn("")
    }

    @Test fun testSetTempBasalPercent() {
        DateTimeZone.setProvider(UTCProvider())

        // mock all the things
        val plugin = OmnipodErosPumpPlugin(
            injector, aapsLogger, TestAapsSchedulers(), rxBusWrapper, null,
            rh, null, null, aapsOmnipodErosManager, commandQueue,
            null, null, null, null,
            rileyLinkUtil, null, null, pumpSync, uiInteraction, erosHistoryDatabase
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
        Assert.assertEquals(result1.absolute, 0.4, 0.01)
        Assert.assertEquals(result1.duration, 30)
        Assert.assertEquals(result2.absolute, 25.0, 0.01)
        Assert.assertEquals(result2.duration, 30000)
        Assert.assertEquals(result3.absolute, 0.0, 0.01)
        Assert.assertEquals(result3.duration, 30)
        Assert.assertEquals(result4.absolute, -1.0, 0.01)
        Assert.assertEquals(result4.duration, -1)
        // this is validated downstream, see TempBasalExtraCommand
        Assert.assertEquals(result5.absolute, -0.25, 0.01)
        Assert.assertEquals(result5.duration, 60)

        // Given zero basal
        `when`(profile.getBasal()).thenReturn(0.0)
        // When
        result1 =
            plugin.setTempBasalPercent(8000, 90, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        result2 =
            plugin.setTempBasalPercent(0, 0, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return zero values
        Assert.assertEquals(result1.absolute, 0.0, 0.01)
        Assert.assertEquals(result1.duration, 90)
        Assert.assertEquals(result2.absolute, -1.0, 0.01)
        Assert.assertEquals(result2.duration, -1)

        // Given unhealthy basal
        `when`(profile.getBasal()).thenReturn(500.0)
        // When treatment
        result1 =
            plugin.setTempBasalPercent(80, 30, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return sane values
        Assert.assertEquals(
            result1.absolute,
            PumpType.OMNIPOD_EROS.determineCorrectBasalSize(500.0 * 0.8),
            0.01
        )
        Assert.assertEquals(result1.duration, 30)

        // Given weird basal
        `when`(profile.getBasal()).thenReturn(1.234567)
        // When treatment
        result1 =
            plugin.setTempBasalPercent(280, 600, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return sane values
        Assert.assertEquals(result1.absolute, 3.4567876, 0.01)
        Assert.assertEquals(result1.duration, 600)

        // Given negative basal
        `when`(profile.getBasal()).thenReturn(-1.234567)
        // When treatment
        result1 =
            plugin.setTempBasalPercent(280, 510, profile, false, PumpSync.TemporaryBasalType.NORMAL)
        // Then return negative value (this is validated further downstream, see TempBasalExtraCommand)
        Assert.assertEquals(result1.absolute, -3.4567876, 0.01)
        Assert.assertEquals(result1.duration, 510)
    }
}