package info.nightscout.implementation.queue

import android.content.Context
import android.os.Handler
import android.os.PowerManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.TestBaseWithProfile
import info.nightscout.androidaps.TestPumpPlugin
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import info.nightscout.database.impl.AppRepository
import info.nightscout.implementation.R
import info.nightscout.implementation.queue.commands.CommandBolus
import info.nightscout.implementation.queue.commands.CommandCustomCommand
import info.nightscout.implementation.queue.commands.CommandExtendedBolus
import info.nightscout.implementation.queue.commands.CommandLoadHistory
import info.nightscout.implementation.queue.commands.CommandTempBasalPercent
import info.nightscout.interfaces.AndroidPermission
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.pump.PumpSync
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.queue.CustomCommand
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.core.Single
import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import java.util.Calendar

class CommandQueueImplementationTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: Constraints
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var sp: SP
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var repository: AppRepository
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var androidPermission: AndroidPermission
    @Mock lateinit var persistenceLayer: PersistenceLayer

    class CommandQueueMocked(
        injector: HasAndroidInjector,
        aapsLogger: AAPSLogger,
        rxBus: RxBus,
        aapsSchedulers: AapsSchedulers,
        rh: ResourceHelper,
        constraintChecker: Constraints,
        profileFunction: ProfileFunction,
        activePlugin: ActivePlugin,
        context: Context,
        sp: SP,
        config: Config,
        dateUtil: DateUtil,
        repository: AppRepository,
        fabricPrivacy: FabricPrivacy,
        androidPermission: AndroidPermission,
        uiInteraction: UiInteraction,
        persistenceLayer: PersistenceLayer
    ) : CommandQueueImplementation(
        injector, aapsLogger, rxBus, aapsSchedulers, rh, constraintChecker, profileFunction,
        activePlugin, context, sp, config, dateUtil, repository, fabricPrivacy,
        androidPermission, uiInteraction, persistenceLayer
    ) {

        override fun notifyAboutNewCommand() : Boolean = true

    }

    val injector = HasAndroidInjector {
        AndroidInjector {
            if (it is Command) {
                it.aapsLogger = aapsLogger
                it.rh = rh
            }
            if (it is CommandTempBasalPercent) {
                it.activePlugin = activePlugin
            }
            if (it is CommandBolus) {
                it.activePlugin = activePlugin
                it.rxBus = rxBus
            }
            if (it is CommandCustomCommand) {
                it.activePlugin = activePlugin
            }
            if (it is CommandExtendedBolus) {
                it.activePlugin = activePlugin
            }
            if (it is CommandLoadHistory) {
                it.activePlugin = activePlugin
            }
            if (it is PumpEnactResult) {
                it.context = context
            }
        }
    }

    private lateinit var commandQueue: CommandQueueImplementation
    private lateinit var testPumpPlugin: TestPumpPlugin

    @BeforeEach
    fun prepare() {
        commandQueue = CommandQueueMocked(
            injector, aapsLogger, rxBus, aapsSchedulers, rh,
            constraintChecker, profileFunction, activePlugin, context, sp,
            config, dateUtil, repository,
            fabricPrivacy, androidPermission, uiInteraction, persistenceLayer
        )
        testPumpPlugin = TestPumpPlugin(injector)

        testPumpPlugin.pumpDescription.basalMinimumRate = 0.1

        `when`(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        `when`(activePlugin.activePump).thenReturn(testPumpPlugin)
        `when`(repository.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(Single.just(ValueWrapper.Existing(effectiveProfileSwitch)))
        `when`(repository.getLastBolusRecord()).thenReturn(
            Bolus(
                timestamp = Calendar.getInstance().also { it.set(2000, 0, 1) }.timeInMillis,
                type = Bolus.Type.NORMAL,
                amount = 0.0
            )
        )
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
        `when`(rh.gs(R.string.connectiontimedout)).thenReturn("Connection timed out")
        `when`(rh.gs(R.string.format_insulin_units)).thenReturn("%1\$.2f U")
        `when`(rh.gs(R.string.goingtodeliver)).thenReturn("Going to deliver %1\$.2f U")
    }

    @Test
    fun commandIsPickedUp() {
        val commandQueue = CommandQueueImplementation(
            injector, aapsLogger, rxBus, aapsSchedulers, rh,
            constraintChecker, profileFunction, activePlugin, context, sp,
            config, dateUtil, repository, fabricPrivacy, androidPermission, uiInteraction, persistenceLayer
        )
        val handler = mock(Handler::class.java)
        `when`(handler.post(anyObject())).thenAnswer { invocation: InvocationOnMock ->
            (invocation.arguments[0] as Runnable).run()
            true
        }
        commandQueue.handler = handler

        // start with empty queue
        Assert.assertEquals(0, commandQueue.size())

        // add bolus command
        commandQueue.bolus(DetailedBolusInfo(), null)
        Assert.assertEquals(1, commandQueue.size())

        commandQueue.waitForFinishedThread()
        Thread.sleep(1000)

        Assert.assertEquals(0, commandQueue.size())
    }

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
        commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        Assert.assertEquals(1, commandQueue.size())

        // add tempbasal percent. it should replace previous TEMPBASAL
        commandQueue.tempBasalPercent(0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        Assert.assertEquals(1, commandQueue.size())

        // cancel tempbasal it should replace previous TEMPBASAL
        commandQueue.cancelTempBasal(false, null)
        Assert.assertEquals(1, commandQueue.size())

        // add extended bolus
        commandQueue.extendedBolus(1.0, 30, null)
        Assert.assertEquals(2, commandQueue.size())

        // add extended should remove previous extended setting
        commandQueue.extendedBolus(1.0, 30, null)
        Assert.assertEquals(2, commandQueue.size())

        // cancel extended bolus should replace previous extended
        commandQueue.cancelExtended(null)
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
        commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
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
        smb.lastKnownBolusTime = System.currentTimeMillis()
        smb.bolusType = DetailedBolusInfo.BolusType.SMB
        commandQueue.bolus(smb, null)
        commandQueue.bolus(DetailedBolusInfo(), null)
        Assert.assertEquals(2, commandQueue.size())

        // when
        commandQueue.cancelAllBoluses(null)

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
        smb.bolusType = DetailedBolusInfo.BolusType.SMB
        val queued: Boolean = commandQueue.bolus(smb, null)

        // then
        Assert.assertFalse(queued)
        Assert.assertEquals(commandQueue.size(), 1)
    }

    @Test
    fun smbIsRejectedIfLastKnownBolusIsOutdated() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        val bolus = DetailedBolusInfo()
        bolus.bolusType = DetailedBolusInfo.BolusType.SMB
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
    fun isSetUserOptionsCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        commandQueue.setUserOptions(null)

        // then
        Assert.assertTrue(commandQueue.isLastScheduled(Command.CommandType.SET_USER_SETTINGS))
        Assert.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.setUserOptions(null)
        Assert.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isLoadEventsCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        commandQueue.loadEvents(null)

        // then
        Assert.assertTrue(commandQueue.isLastScheduled(Command.CommandType.LOAD_EVENTS))
        Assert.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.loadEvents(null)
        Assert.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isLoadTDDsCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        commandQueue.loadTDDs(null)

        // then
        Assert.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.loadTDDs(null)
        Assert.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isLoadHistoryCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        commandQueue.loadHistory(0, null)

        // then
        Assert.assertTrue(commandQueue.isLastScheduled(Command.CommandType.LOAD_HISTORY))
        Assert.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.loadHistory(0, null)
        Assert.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isProfileSetCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        testPumpPlugin.isProfileSet = true
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
                Assert.assertFalse(result.enacted)
            }
        })

        // then
        // the same profile -> ignore
        Assert.assertEquals(0, commandQueue.size())
        // different should be added
        testPumpPlugin.isProfileSet = false
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
                Assert.assertTrue(result.enacted)
            }
        })
        Assert.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                Assert.assertTrue(result.success)
            }
        })
        Assert.assertEquals(1, commandQueue.size())
        testPumpPlugin.isProfileSet = true
    }

    @Test
    fun isStopCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        commandQueue.stopPump(null)

        // then
        Assert.assertTrue(commandQueue.isLastScheduled(Command.CommandType.STOP_PUMP))
        Assert.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isStarCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        commandQueue.startPump(null)

        // then
        Assert.assertTrue(commandQueue.isLastScheduled(Command.CommandType.START_PUMP))
        Assert.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isSetTbrNotificationCommandInQueue() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        commandQueue.setTBROverNotification(null, true)

        // then
        Assert.assertTrue(commandQueue.isLastScheduled(Command.CommandType.INSIGHT_SET_TBR_OVER_ALARM))
        Assert.assertEquals(1, commandQueue.size())
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

    @Test
    fun readStatusTwiceIsNotAllowed() {
        // given
        Assert.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.readStatus("1", null)
        val queued2 = commandQueue.readStatus("2", null)

        // then
        Assert.assertTrue(queued1)
        Assert.assertFalse(queued2)
        Assert.assertEquals(1, commandQueue.size())
        Assert.assertTrue(commandQueue.statusInQueue())
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
