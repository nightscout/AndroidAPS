package info.nightscout.androidaps.plugins.aps.loop

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.IobCobCalculator
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.utils.JsonHelper.safeGetDouble
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class)
class APSResultTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var sp: SP
    @Mock lateinit var iobCobCalculator: IobCobCalculator

    private lateinit var testPumpPlugin: TestPumpPlugin
    private val injector = HasAndroidInjector { AndroidInjector { } }

    private var closedLoopEnabled = Constraint(false)

    @Test
    fun changeRequestedTest() {

        val apsResult = APSResult { AndroidInjector { } }
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintChecker
                it.sp = sp
                it.activePlugin = activePluginProvider
                it.iobCobCalculator = iobCobCalculator
                it.profileFunction = profileFunction
                it.resourceHelper = resourceHelper
            }

        // BASAL RATE IN TEST PROFILE IS 1U/h

        // **** PERCENT pump ****
        testPumpPlugin.pumpDescription.fillFor(PumpType.CELLNOVO) // % based
        apsResult.usePercent(true)

        // closed loop mode return original request
        closedLoopEnabled.set(aapsLogger, true)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(false)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        apsResult.tempBasalRequested(true).percent(200).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // open loop
        closedLoopEnabled.set(aapsLogger, false)
        // no change requested
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(false)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request 100% when no temp is running
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(true).percent(100).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request equal temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 70.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).percent(70).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request zero temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 10.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).percent(0).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request high temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 190.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).percent(200).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request slightly different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 70.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).percent(80).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 70.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).percent(120).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // it should work with absolute temps too
        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 1.0, duration = 30, isAbsolute = true, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).percent(100).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 2.0, duration = 30, isAbsolute = true, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).percent(50).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // **** ABSOLUTE pump ****
        testPumpPlugin.pumpDescription.fillFor(PumpType.MEDTRONIC_515_715) // U/h based
        apsResult.usePercent(false)

        // open loop
        closedLoopEnabled.set(aapsLogger, false)
        // request 100% when no temp is running
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(true).rate(1.0).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request equal temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 2.0, duration = 30, isAbsolute = true, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(2.0).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 200.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(2.0).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request zero temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 0.1, duration = 30, isAbsolute = true, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(0.0).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request high temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 34.9, duration = 30, isAbsolute = true, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(35.0).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request slightly different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 1.1, duration = 30, isAbsolute = true, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(1.2).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 1.1, duration = 30, isAbsolute = true, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(1.5).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // it should work with percent temps too
        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 110.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(1.1).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(timestamp = 0, rate = 200.0, duration = 30, isAbsolute = false, type = TemporaryBasal.Type.NORMAL))
        apsResult.tempBasalRequested(true).rate(0.5).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)
    }

    @Test fun cloneTest() {
        val apsResult = APSResult { AndroidInjector { } }
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintChecker
                it.sp = sp
                it.activePlugin = activePluginProvider
                it.iobCobCalculator = iobCobCalculator
                it.profileFunction = profileFunction
                it.resourceHelper = resourceHelper
            }
        apsResult.rate(10.0)
        val apsResult2 = apsResult.newAndClone(injector)
        Assert.assertEquals(apsResult.rate, apsResult2.rate, 0.0)
    }

    @Test fun jsonTest() {
        closedLoopEnabled.set(aapsLogger, true)
        val apsResult = APSResult { AndroidInjector { } }
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintChecker
                it.sp = sp
                it.activePlugin = activePluginProvider
                it.iobCobCalculator = iobCobCalculator
                it.profileFunction = profileFunction
                it.resourceHelper = resourceHelper
            }
        apsResult.rate(20.0).tempBasalRequested(true)
        Assert.assertEquals(20.0, safeGetDouble(apsResult.json(), "rate"), 0.0)
        apsResult.rate(20.0).tempBasalRequested(false)
        Assert.assertEquals(false, apsResult.json()?.has("rate"))
    }

    @Before
    fun prepare() {
        testPumpPlugin = TestPumpPlugin(profileInjector)
        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(closedLoopEnabled)
        `when`(activePluginProvider.activePump).thenReturn(testPumpPlugin)
        `when`(sp.getDouble(ArgumentMatchers.anyInt(), ArgumentMatchers.anyDouble())).thenReturn(30.0)
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
    }
}