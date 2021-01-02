package info.nightscout.androidaps.queue

import android.content.Context
import dagger.Lazy
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.commands.Command
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.util.*

@RunWith(PowerMockRunner::class)
@PrepareForTest(ConstraintChecker::class, VirtualPumpPlugin::class, ToastUtils::class, Context::class, TreatmentsPlugin::class, FabricPrivacy::class)
class CommandQueueTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintChecker
    @Mock lateinit var lazyActivePlugin: Lazy<ActivePluginProvider>
    @Mock lateinit var activePlugin: ActivePluginProvider
    @Mock lateinit var context: Context
    @Mock lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Mock lateinit var sp: SP

    private val buildHelper = BuildHelper(Config())

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Command) {
                it.aapsLogger = aapsLogger
                it.resourceHelper = resourceHelper
            }
        }
    }

    lateinit var commandQueue: CommandQueue

    @Before
    fun prepare() {
        commandQueue = CommandQueue(injector, aapsLogger, rxBus, resourceHelper, constraintChecker, profileFunction, lazyActivePlugin, context, sp, buildHelper, fabricPrivacy)

        val pumpDescription = PumpDescription()
        pumpDescription.basalMinimumRate = 0.1

        `when`(lazyActivePlugin.get()).thenReturn(activePlugin)
        `when`(activePlugin.activePump).thenReturn(virtualPumpPlugin)
        `when`(virtualPumpPlugin.pumpDescription).thenReturn(pumpDescription)
        `when`(virtualPumpPlugin.isThisProfileSet(anyObject())).thenReturn(false)
        `when`(activePlugin.activeTreatments).thenReturn(treatmentsPlugin)
        `when`(treatmentsPlugin.lastBolusTime).thenReturn(Date(100, 0, 1).time)
        `when`(profileFunction.getProfile()).thenReturn(validProfile)

        val bolusConstraint = Constraint(0.0)
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        `when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        val carbsConstraint = Constraint(0)
        `when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(carbsConstraint)
        val rateConstraint = Constraint(0.0)
        `when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(rateConstraint)
        val percentageConstraint = Constraint(0)
        `when`(constraintChecker.applyBasalPercentConstraints(anyObject(), anyObject())).thenReturn(percentageConstraint)
    }

    /*
        @Test
        fun doTests() {

            // start with empty queue
            Assert.assertEquals(0, commandQueue.size())

            // add bolus command
            commandQueue.bolus(DetailedBolusInfo(), null)
            Assert.assertEquals(1, commandQueue.size())

            // add READSTATUS
            commandQueue.readStatus("anyString", null)
            Assert.assertEquals(2, commandQueue.size())

            // adding another bolus should remove the first one (size still == 2)
            commandQueue.bolus(DetailedBolusInfo(), null)
            Assert.assertEquals(2, commandQueue.size())

            // clear the queue should reset size
            commandQueue.clear()
            Assert.assertEquals(0, commandQueue.size())

            // add tempbasal
            commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, null)
            Assert.assertEquals(1, commandQueue.size())

            // add tempbasal percent. it should replace previous TEMPBASAL
            commandQueue.tempBasalPercent(0, 30, true, validProfile, null)
            Assert.assertEquals(1, commandQueue.size())

            // add extended bolus
            commandQueue.extendedBolus(1.0, 30, null)
            Assert.assertEquals(2, commandQueue.size())

            // add cancel temp basal should remove previous 2 temp basal setting
            commandQueue.extendedBolus(1.0, 30, null)
            Assert.assertEquals(2, commandQueue.size())

            // cancel extended bolus should replace previous extended
            commandQueue.extendedBolus(1.0, 30, null)
            Assert.assertEquals(2, commandQueue.size())

            // add setProfile
            // TODO: this crash the test
    //        commandQueue.setProfile(validProfile, null)
    //        Assert.assertEquals(3, commandQueue.size())

            // add loadHistory
            commandQueue.loadHistory(0.toByte(), null)
            Assert.assertEquals(3, commandQueue.size())

            // add loadEvents
            commandQueue.loadEvents(null)
            Assert.assertEquals(4, commandQueue.size())
            commandQueue.clear()
            commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, null)
            commandQueue.pickup()
            Assert.assertEquals(0, commandQueue.size())
            Assert.assertNotNull(commandQueue.performing)
            Assert.assertEquals(Command.CommandType.TEMPBASAL, commandQueue.performing?.commandType)
            commandQueue.resetPerforming()
            Assert.assertNull(commandQueue.performing)
        }

        @Test
        fun callingCancelAllBolusesClearsQueue() {
            // given
            Assert.assertEquals(0, commandQueue.size())
            val smb = DetailedBolusInfo()
            smb.lastKnownBolusTime = DateUtil.now()
            smb.isSMB = true
            commandQueue.bolus(smb, null)
            commandQueue.bolus(DetailedBolusInfo(), null)
            Assert.assertEquals(2, commandQueue.size())

            // when
            commandQueue.cancelAllBoluses()

            // then
            Assert.assertEquals(0, commandQueue.size())
        }

        @Test
        fun smbIsRejectedIfABolusIsQueued() {
            // given
            Assert.assertEquals(0, commandQueue.size())

            // when
            commandQueue.bolus(DetailedBolusInfo(), null)
            val smb = DetailedBolusInfo()
            smb.isSMB = true
            val queued: Boolean = commandQueue.bolus(smb, null)

            // then
            Assert.assertFalse(queued)
            Assert.assertEquals(commandQueue.size(), 1)
        }
    */
    @Test
    fun smbIsRejectedIfLastKnownBolusIsOutdated() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        val bolus = DetailedBolusInfo()
        bolus.isSMB = true
        bolus.lastKnownBolusTime = 0
        val queued: Boolean = commandQueue.bolus(bolus, null)

        // then
        Assert.assertFalse(queued)
        Assert.assertEquals(commandQueue.size(), 0)
    }

    @Test
    fun isCustomCommandRunning() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand2(), null)
        commandQueue.pickup()

        // then
        Assert.assertTrue(queued1)
        Assert.assertTrue(queued2)
        Assert.assertTrue(commandQueue.isCustomCommandInQueue(CustomCommand1::class.java))
        Assert.assertTrue(commandQueue.isCustomCommandInQueue(CustomCommand2::class.java))
        Assert.assertFalse(commandQueue.isCustomCommandInQueue(CustomCommand3::class.java))

        Assert.assertTrue(commandQueue.isCustomCommandRunning(CustomCommand1::class.java))
        Assert.assertFalse(commandQueue.isCustomCommandRunning(CustomCommand2::class.java))
        Assert.assertFalse(commandQueue.isCustomCommandRunning(CustomCommand3::class.java))


        Assert.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isCustomCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand2(), null)

        // then
        Assert.assertTrue(queued1)
        Assert.assertTrue(queued2)
        Assert.assertTrue(commandQueue.isCustomCommandInQueue(CustomCommand1::class.java))
        Assert.assertTrue(commandQueue.isCustomCommandInQueue(CustomCommand2::class.java))
        Assert.assertFalse(commandQueue.isCustomCommandInQueue(CustomCommand3::class.java))
        Assert.assertEquals(2, commandQueue.size())
    }

    @Test
    fun differentCustomCommandsAllowed() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand2(), null)

        // then
        Assert.assertTrue(queued1)
        Assert.assertTrue(queued2)
        Assert.assertEquals(2, commandQueue.size())
    }

    @Test
    fun sameCustomCommandNotAllowed() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand1(), null)

        // then
        Assert.assertTrue(queued1)
        Assert.assertFalse(queued2)
        Assert.assertEquals(1, commandQueue.size())
    }

    private class CustomCommand1 : CustomCommand {
        override val statusDescription: String
            get() = "CUSTOM COMMAND 1"
    }

    private class CustomCommand2 : CustomCommand {
        override val statusDescription: String
            get() = "CUSTOM COMMAND 2"
    }

    private class CustomCommand3 : CustomCommand {
        override val statusDescription: String
            get() = "CUSTOM COMMAND 3"
    }
}