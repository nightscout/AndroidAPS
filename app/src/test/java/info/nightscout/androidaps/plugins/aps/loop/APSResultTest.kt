package info.nightscout.androidaps.plugins.aps.loop

import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.pump.common.defs.PumpType
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
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
@PrepareForTest(ConstraintChecker::class, VirtualPumpPlugin::class)
class APSResultTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var sp: SP
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin

    private val injector = HasAndroidInjector { AndroidInjector { } }

    private var closedLoopEnabled = Constraint(false)
    private val pumpDescription = PumpDescription()

    @Test
    fun changeRequestedTest() {

        val apsResult = APSResult(HasAndroidInjector { AndroidInjector { Unit } })
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintChecker
                it.sp = sp
                it.activePlugin = activePluginProvider
                it.treatmentsPlugin = treatmentsPlugin
                it.profileFunction = profileFunction
                it.resourceHelper = resourceHelper
            }

        // BASAL RATE IN TEST PROFILE IS 1U/h

        // **** PERCENT pump ****
        pumpDescription.setPumpDescription(PumpType.Cellnovo1) // % based
        apsResult.usePercent(true)

        // closed loop mode return original request
        closedLoopEnabled.set(aapsLogger, true)
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(false)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        apsResult.tempBasalRequested(true).percent(200).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // open loop
        closedLoopEnabled.set(aapsLogger, false)
        // no change requested
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(false)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request 100% when no temp is running
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(true).percent(100).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request equal temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(70).duration(30))
        apsResult.tempBasalRequested(true).percent(70).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request zero temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(10).duration(30))
        apsResult.tempBasalRequested(true).percent(0).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request high temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(190).duration(30))
        apsResult.tempBasalRequested(true).percent(200).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request slightly different temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(70).duration(30))
        apsResult.tempBasalRequested(true).percent(80).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request different temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(70).duration(30))
        apsResult.tempBasalRequested(true).percent(120).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // it should work with absolute temps too
        // request different temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).absolute(1.0).duration(30))
        apsResult.tempBasalRequested(true).percent(100).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).absolute(2.0).duration(30))
        apsResult.tempBasalRequested(true).percent(50).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // **** ABSOLUTE pump ****
        virtualPumpPlugin.pumpDescription.setPumpDescription(PumpType.Medtronic_515_715) // U/h based
        apsResult.usePercent(false)

        // open loop
        closedLoopEnabled.set(aapsLogger, false)
        // request 100% when no temp is running
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(null)
        apsResult.tempBasalRequested(true).rate(1.0).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request equal temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).absolute(2.0).duration(30))
        apsResult.tempBasalRequested(true).rate(2.0).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(200).duration(30))
        apsResult.tempBasalRequested(true).rate(2.0).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request zero temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).absolute(0.1).duration(30))
        apsResult.tempBasalRequested(true).rate(0.0).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request high temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).absolute(34.9).duration(30))
        apsResult.tempBasalRequested(true).rate(35.0).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // request slightly different temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).absolute(1.1).duration(30))
        apsResult.tempBasalRequested(true).rate(1.2).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)

        // request different temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).absolute(1.1).duration(30))
        apsResult.tempBasalRequested(true).rate(1.5).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)

        // it should work with percent temps too
        // request different temp
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(110).duration(30))
        apsResult.tempBasalRequested(true).rate(1.1).duration(30)
        Assert.assertEquals(false, apsResult.isChangeRequested)
        `when`(treatmentsPlugin.getTempBasalFromHistory(ArgumentMatchers.anyLong())).thenReturn(TemporaryBasal(injector).percent(200).duration(30))
        apsResult.tempBasalRequested(true).rate(0.5).duration(30)
        Assert.assertEquals(true, apsResult.isChangeRequested)
    }

    @Test fun cloneTest() {
        val apsResult = APSResult(HasAndroidInjector { AndroidInjector { Unit } })
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintChecker
                it.sp = sp
                it.activePlugin = activePluginProvider
                it.treatmentsPlugin = treatmentsPlugin
                it.profileFunction = profileFunction
                it.resourceHelper = resourceHelper
            }
        apsResult.rate(10.0)
        val apsResult2 = apsResult.newAndClone(injector)
        Assert.assertEquals(apsResult.rate, apsResult2.rate, 0.0)
    }

    @Test fun jsonTest() {
        closedLoopEnabled.set(aapsLogger, true)
        val apsResult = APSResult(HasAndroidInjector { AndroidInjector { Unit } })
            .also {
                it.aapsLogger = aapsLogger
                it.constraintChecker = constraintChecker
                it.sp = sp
                it.activePlugin = activePluginProvider
                it.treatmentsPlugin = treatmentsPlugin
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
        `when`(constraintChecker.isClosedLoopAllowed()).thenReturn(closedLoopEnabled)
        `when`(activePluginProvider.activePump).thenReturn(virtualPumpPlugin)
        `when`(sp.getDouble(ArgumentMatchers.anyInt(), ArgumentMatchers.anyDouble())).thenReturn(30.0)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(profileFunction.getProfile()).thenReturn(validProfile)
    }
}