package info.nightscout.implementation.queue

import android.content.Context
import android.os.Handler
import android.os.PowerManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.Bolus
import info.nightscout.database.impl.AppRepository
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
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.sharedtests.TestBaseWithProfile
import info.nightscout.sharedtests.TestPumpPlugin
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
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
        persistenceLayer: PersistenceLayer,
        decimalFormatter: DecimalFormatter
    ) : CommandQueueImplementation(
        injector, aapsLogger, rxBus, aapsSchedulers, rh, constraintChecker, profileFunction,
        activePlugin, context, sp, config, dateUtil, repository, fabricPrivacy,
        androidPermission, uiInteraction, persistenceLayer, decimalFormatter
    ) {

        override fun notifyAboutNewCommand(): Boolean = true

    }

    private val injector = HasAndroidInjector {
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

    @BeforeEach
    fun prepare() {
        commandQueue = CommandQueueMocked(
            injector, aapsLogger, rxBus, aapsSchedulers, rh,
            constraintChecker, profileFunction, activePlugin, context, sp,
            config, dateUtil, repository,
            fabricPrivacy, androidPermission, uiInteraction, persistenceLayer, decimalFormatter
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
        `when`(rh.gs(info.nightscout.core.ui.R.string.connectiontimedout)).thenReturn("Connection timed out")
        `when`(rh.gs(info.nightscout.interfaces.R.string.format_insulin_units)).thenReturn("%1\$.2f U")
        `when`(rh.gs(info.nightscout.core.ui.R.string.goingtodeliver)).thenReturn("Going to deliver %1\$.2f U")
    }

    @Test
    fun commandIsPickedUp() {
        val commandQueue = CommandQueueImplementation(
            injector, aapsLogger, rxBus, aapsSchedulers, rh,
            constraintChecker, profileFunction, activePlugin, context, sp,
            config, dateUtil, repository, fabricPrivacy, androidPermission, uiInteraction, persistenceLayer, decimalFormatter
        )
        val handler = mock(Handler::class.java)
        `when`(handler.post(anyObject())).thenAnswer { invocation: InvocationOnMock ->
            (invocation.arguments[0] as Runnable).run()
            true
        }
        commandQueue.handler = handler

        // start with empty queue
        Assertions.assertEquals(0, commandQueue.size())

        // add bolus command
        commandQueue.bolus(DetailedBolusInfo(), null)
        Assertions.assertEquals(1, commandQueue.size())

        commandQueue.waitForFinishedThread()
        Thread.sleep(1000)

        Assertions.assertEquals(0, commandQueue.size())
    }

    @Test
    fun doTests() {

        // start with empty queue
        Assertions.assertEquals(0, commandQueue.size())

        // add bolus command
        commandQueue.bolus(DetailedBolusInfo(), null)
        Assertions.assertEquals(1, commandQueue.size())

        // add READSTATUS
        commandQueue.readStatus("anyString", null)
        Assertions.assertEquals(2, commandQueue.size())

        // adding another bolus should remove the first one (size still == 2)
        commandQueue.bolus(DetailedBolusInfo(), null)
        Assertions.assertEquals(2, commandQueue.size())

        // clear the queue should reset size
        commandQueue.clear()
        Assertions.assertEquals(0, commandQueue.size())

        // add tempbasal
        commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        Assertions.assertEquals(1, commandQueue.size())

        // add tempbasal percent. it should replace previous TEMPBASAL
        commandQueue.tempBasalPercent(0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        Assertions.assertEquals(1, commandQueue.size())

        // cancel tempbasal it should replace previous TEMPBASAL
        commandQueue.cancelTempBasal(false, null)
        Assertions.assertEquals(1, commandQueue.size())

        // add extended bolus
        commandQueue.extendedBolus(1.0, 30, null)
        Assertions.assertEquals(2, commandQueue.size())

        // add extended should remove previous extended setting
        commandQueue.extendedBolus(1.0, 30, null)
        Assertions.assertEquals(2, commandQueue.size())

        // cancel extended bolus should replace previous extended
        commandQueue.cancelExtended(null)
        Assertions.assertEquals(2, commandQueue.size())

        // add setProfile
        // TODO: this crash the test
        //        commandQueue.setProfile(validProfile, null)
        //        Assertions.assertEquals(3, commandQueue.size())

        // add loadHistory
        commandQueue.loadHistory(0.toByte(), null)
        Assertions.assertEquals(3, commandQueue.size())

        // add loadEvents
        commandQueue.loadEvents(null)
        Assertions.assertEquals(4, commandQueue.size())

        // add clearAlarms
        commandQueue.clearAlarms(null)
        Assertions.assertEquals(5, commandQueue.size())

        // add deactivate
        commandQueue.deactivate(null)
        Assertions.assertEquals(6, commandQueue.size())

        // add updateTime
        commandQueue.updateTime(null)
        Assertions.assertEquals(7, commandQueue.size())

        commandQueue.clear()
        commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        commandQueue.pickup()
        Assertions.assertEquals(0, commandQueue.size())
        Assertions.assertNotNull(commandQueue.performing)
        Assertions.assertEquals(Command.CommandType.TEMPBASAL, commandQueue.performing?.commandType)
        commandQueue.resetPerforming()
        Assertions.assertNull(commandQueue.performing)
    }

    @Test
    fun callingCancelAllBolusesClearsQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())
        val smb = DetailedBolusInfo()
        smb.lastKnownBolusTime = System.currentTimeMillis()
        smb.bolusType = DetailedBolusInfo.BolusType.SMB
        commandQueue.bolus(smb, null)
        commandQueue.bolus(DetailedBolusInfo(), null)
        Assertions.assertEquals(2, commandQueue.size())

        // when
        commandQueue.cancelAllBoluses(null)

        // then
        Assertions.assertEquals(0, commandQueue.size())
    }

    @Test
    fun smbIsRejectedIfABolusIsQueued() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.bolus(DetailedBolusInfo(), null)
        val smb = DetailedBolusInfo()
        smb.bolusType = DetailedBolusInfo.BolusType.SMB
        val queued: Boolean = commandQueue.bolus(smb, null)

        // then
        Assertions.assertFalse(queued)
        Assertions.assertEquals(commandQueue.size(), 1)
    }

    @Test
    fun smbIsRejectedIfLastKnownBolusIsOutdated() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        val bolus = DetailedBolusInfo()
        bolus.bolusType = DetailedBolusInfo.BolusType.SMB
        bolus.lastKnownBolusTime = 0
        val queued: Boolean = commandQueue.bolus(bolus, null)

        // then
        Assertions.assertFalse(queued)
        Assertions.assertEquals(commandQueue.size(), 0)
    }

    @Test
    fun isCustomCommandRunning() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand2(), null)
        commandQueue.pickup()

        // then
        Assertions.assertTrue(queued1)
        Assertions.assertTrue(queued2)
        Assertions.assertTrue(commandQueue.isCustomCommandInQueue(CustomCommand1::class.java))
        Assertions.assertTrue(commandQueue.isCustomCommandInQueue(CustomCommand2::class.java))
        Assertions.assertFalse(commandQueue.isCustomCommandInQueue(CustomCommand3::class.java))

        Assertions.assertTrue(commandQueue.isCustomCommandRunning(CustomCommand1::class.java))
        Assertions.assertFalse(commandQueue.isCustomCommandRunning(CustomCommand2::class.java))
        Assertions.assertFalse(commandQueue.isCustomCommandRunning(CustomCommand3::class.java))

        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isSetUserOptionsCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.setUserOptions(null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.SET_USER_SETTINGS))
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.setUserOptions(null)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isLoadEventsCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.loadEvents(null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.LOAD_EVENTS))
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.loadEvents(null)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isClearAlarmsCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.clearAlarms(null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.CLEAR_ALARMS))
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.clearAlarms(null)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isDeactivateCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.deactivate(null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.DEACTIVATE))
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.deactivate(null)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isUpdateTimeCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.updateTime(null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.UPDATE_TIME))
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.updateTime(null)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isLoadTDDsCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.loadTDDs(null)

        // then
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.loadTDDs(null)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isLoadHistoryCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.loadHistory(0, null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.LOAD_HISTORY))
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.loadHistory(0, null)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isProfileSetCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        testPumpPlugin.isProfileSet = true
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
                Assertions.assertFalse(result.enacted)
            }
        })

        // then
        // the same profile -> ignore
        Assertions.assertEquals(0, commandQueue.size())
        // different should be added
        testPumpPlugin.isProfileSet = false
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
                Assertions.assertTrue(result.enacted)
            }
        })
        Assertions.assertEquals(1, commandQueue.size())
        // next should be ignored
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                Assertions.assertTrue(result.success)
            }
        })
        Assertions.assertEquals(1, commandQueue.size())
        testPumpPlugin.isProfileSet = true
    }

    @Test
    fun isStopCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.stopPump(null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.STOP_PUMP))
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isStarCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.startPump(null)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.START_PUMP))
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun isSetTbrNotificationCommandInQueue() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        commandQueue.setTBROverNotification(null, true)

        // then
        Assertions.assertTrue(commandQueue.isLastScheduled(Command.CommandType.INSIGHT_SET_TBR_OVER_ALARM))
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun differentCustomCommandsAllowed() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand2(), null)

        // then
        Assertions.assertTrue(queued1)
        Assertions.assertTrue(queued2)
        Assertions.assertEquals(2, commandQueue.size())
    }

    @Test
    fun sameCustomCommandNotAllowed() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand1(), null)

        // then
        Assertions.assertTrue(queued1)
        Assertions.assertFalse(queued2)
        Assertions.assertEquals(1, commandQueue.size())
    }

    @Test
    fun readStatusTwiceIsNotAllowed() {
        // given
        Assertions.assertEquals(0, commandQueue.size())

        // when
        val queued1 = commandQueue.readStatus("1", null)
        val queued2 = commandQueue.readStatus("2", null)

        // then
        Assertions.assertTrue(queued1)
        Assertions.assertFalse(queued2)
        Assertions.assertEquals(1, commandQueue.size())
        Assertions.assertTrue(commandQueue.statusInQueue())
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
