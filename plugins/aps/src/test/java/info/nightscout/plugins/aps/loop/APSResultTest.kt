package info.nightscout.plugins.aps.loop

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.core.utils.JsonHelper.safeGetDouble
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.interfaces.aps.APSResult
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.interfaces.pump.defs.PumpType
import info.nightscout.plugins.aps.APSResultObject
import info.nightscout.sharedtests.TestBaseWithProfile
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.`when`

class APSResultTest : TestBaseWithProfile() {

    @Mock lateinit var constraintsChecker: ConstraintsChecker

    private val injector = HasAndroidInjector { AndroidInjector { } }

    private lateinit var closedLoopEnabled: Constraint<Boolean>

    private fun APSResult.percent(percent: Int): APSResult {
        this.percent = percent
        return this
    }

    private fun APSResult.rate(rate: Double): APSResult {
        this.rate = rate
        return this
    }

    private fun APSResult.duration(duration: Int): APSResult {
        this.duration = duration
        return this
    }

    private fun APSResult.usePercent(usePercent: Boolean): APSResult {
        this.usePercent = usePercent
        return this
    }

    private fun APSResult.tempBasalRequested(tempBasalRequested: Boolean): APSResult {
        this.isTempBasalRequested = tempBasalRequested
        return this
    }

    @Test
    fun changeRequestedTest() {

        val apsResult = APSResultObject { AndroidInjector { } }
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintsChecker
                it.sp = sp
                it.activePlugin = activePlugin
                it.iobCobCalculator = iobCobCalculator
                it.profileFunction = profileFunction
                it.rh = rh
            }

        // BASAL RATE IN TEST PROFILE IS 1U/h

        // **** PERCENT pump ****
        testPumpPlugin.pumpDescription.fillFor(PumpType.CELLNOVO) // % based
        apsResult.usePercent(true)

        // closed loop mode return original request
        closedLoopEnabled.set(true)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(false)
        Assertions.assertEquals(false, apsResult.isChangeRequested)
        apsResult.tempBasalRequested(true).percent(200).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // open loop
        closedLoopEnabled.set(false)
        // no change requested
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(false)
        Assertions.assertEquals(false, apsResult.isChangeRequested)

        // request 100% when no temp is running
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(true).percent(100).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)

        // request equal temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 70.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).percent(70).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)

        // request zero temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 10.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).percent(0).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // request high temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 190.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).percent(200).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // request slightly different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 70.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).percent(80).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)

        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 70.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).percent(120).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // it should work with absolute temps too
        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 1.0,
                duration = 30,
                isAbsolute = true,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).percent(100).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 2.0,
                duration = 30,
                isAbsolute = true,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).percent(50).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // **** ABSOLUTE pump ****
        testPumpPlugin.pumpDescription.fillFor(PumpType.MEDTRONIC_515_715) // U/h based
        apsResult.usePercent(false)

        // open loop
        closedLoopEnabled.set(false)
        // request 100% when no temp is running
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(true).rate(1.0).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)

        // request equal temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 2.0,
                duration = 30,
                isAbsolute = true,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(2.0).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 200.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(2.0).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)

        // request zero temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 0.1,
                duration = 30,
                isAbsolute = true,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(0.0).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // request high temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 34.9,
                duration = 30,
                isAbsolute = true,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(35.0).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // request slightly different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 1.1,
                duration = 30,
                isAbsolute = true,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(1.2).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)

        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 1.1,
                duration = 30,
                isAbsolute = true,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(1.5).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)

        // it should work with percent temps too
        // request different temp
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 110.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(1.1).duration(30)
        Assertions.assertEquals(false, apsResult.isChangeRequested)
        `when`(iobCobCalculator.getTempBasalIncludingConvertedExtended(ArgumentMatchers.anyLong())).thenReturn(
            TemporaryBasal(
                timestamp = 0,
                rate = 200.0,
                duration = 30,
                isAbsolute = false,
                type = TemporaryBasal.Type.NORMAL
            )
        )
        apsResult.tempBasalRequested(true).rate(0.5).duration(30)
        Assertions.assertEquals(true, apsResult.isChangeRequested)
    }

    @Test fun cloneTest() {
        val apsResult = APSResultObject(injector)
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintsChecker
                it.sp = sp
                it.activePlugin = activePlugin
                it.iobCobCalculator = iobCobCalculator
                it.profileFunction = profileFunction
                it.rh = rh
            }
        apsResult.rate(10.0)
        val apsResult2 = apsResult.newAndClone(injector)
        Assertions.assertEquals(apsResult.rate, apsResult2.rate, 0.0)
    }

    @Test fun jsonTest() {
        closedLoopEnabled.set(true)
        val apsResult = APSResultObject(injector)
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintsChecker
                it.sp = sp
                it.activePlugin = activePlugin
                it.iobCobCalculator = iobCobCalculator
                it.profileFunction = profileFunction
                it.rh = rh
            }
        apsResult.rate(20.0).tempBasalRequested(true)
        Assertions.assertEquals(20.0, safeGetDouble(apsResult.json(), "rate"), 0.0)
        apsResult.rate(20.0).tempBasalRequested(false)
        Assertions.assertEquals(false, apsResult.json()?.has("rate"))
    }

    @BeforeEach
    fun prepare() {
        closedLoopEnabled = ConstraintObject(false, aapsLogger)
        `when`(constraintsChecker.isClosedLoopAllowed()).thenReturn(closedLoopEnabled)
        `when`(sp.getDouble(ArgumentMatchers.anyInt(), ArgumentMatchers.anyDouble())).thenReturn(30.0)
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
    }
}