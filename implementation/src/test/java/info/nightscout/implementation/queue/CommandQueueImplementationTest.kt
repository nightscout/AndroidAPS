package info.nightscout.implementation.queue

import com.google.common.truth.Truth.assertThat
import android.content.Context
import android.os.Handler
import android.os.PowerManager
import dagger.android.AndroidInjector
import dagger.android.HasAndroidInjector
import info.nightscout.core.constraints.ConstraintObject
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
import info.nightscout.interfaces.constraints.ConstraintsChecker
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.invocation.InvocationOnMock
import java.util.Calendar

class CommandQueueImplementationTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
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
        constraintChecker: ConstraintsChecker,
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

        val bolusConstraint = ConstraintObject(0.0, aapsLogger)
        `when`(constraintChecker.applyBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        `when`(constraintChecker.applyExtendedBolusConstraints(anyObject())).thenReturn(bolusConstraint)
        val carbsConstraint = ConstraintObject(0, aapsLogger)
        `when`(constraintChecker.applyCarbsConstraints(anyObject())).thenReturn(carbsConstraint)
        val rateConstraint = ConstraintObject(0.0, aapsLogger)
        `when`(constraintChecker.applyBasalConstraints(anyObject(), anyObject())).thenReturn(rateConstraint)
        val percentageConstraint = ConstraintObject(0, aapsLogger)
        `when`(constraintChecker.applyBasalPercentConstraints(anyObject(), anyObject())).thenReturn(percentageConstraint)
        `when`(rh.gs(info.nightscout.core.ui.R.string.connectiontimedout)).thenReturn("Connection timed out")
        `when`(rh.gs(info.nightscout.core.ui.R.string.format_insulin_units)).thenReturn("%1\$.2f U")
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
        assertThat(commandQueue.size()).isEqualTo(0)

        // add bolus command
        commandQueue.bolus(DetailedBolusInfo(), null)
        assertThat(commandQueue.size()).isEqualTo(1)

        commandQueue.waitForFinishedThread()
        Thread.sleep(1000)

        assertThat(commandQueue.size()).isEqualTo(0)
    }

    @Test
    fun doTests() {

        // start with empty queue
        assertThat(commandQueue.size()).isEqualTo(0)

        // add bolus command
        commandQueue.bolus(DetailedBolusInfo(), null)
        assertThat(commandQueue.size()).isEqualTo(1)

        // add READSTATUS
        commandQueue.readStatus("anyString", null)
        assertThat(commandQueue.size()).isEqualTo(2)

        // adding another bolus should remove the first one (size still == 2)
        commandQueue.bolus(DetailedBolusInfo(), null)
        assertThat(commandQueue.size()).isEqualTo(2)

        // clear the queue should reset size
        commandQueue.clear()
        assertThat(commandQueue.size()).isEqualTo(0)

        // add tempbasal
        commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        assertThat(commandQueue.size()).isEqualTo(1)

        // add tempbasal percent. it should replace previous TEMPBASAL
        commandQueue.tempBasalPercent(0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        assertThat(commandQueue.size()).isEqualTo(1)

        // cancel tempbasal it should replace previous TEMPBASAL
        commandQueue.cancelTempBasal(false, null)
        assertThat(commandQueue.size()).isEqualTo(1)

        // add extended bolus
        commandQueue.extendedBolus(1.0, 30, null)
        assertThat(commandQueue.size()).isEqualTo(2)

        // add extended should remove previous extended setting
        commandQueue.extendedBolus(1.0, 30, null)
        assertThat(commandQueue.size()).isEqualTo(2)

        // cancel extended bolus should replace previous extended
        commandQueue.cancelExtended(null)
        assertThat(commandQueue.size()).isEqualTo(2)

        // add setProfile
        // TODO: this crash the test
        //        commandQueue.setProfile(validProfile, null)
        //        assertThat(commandQueue.size()).isEqualTo(3)

        // add loadHistory
        commandQueue.loadHistory(0.toByte(), null)
        assertThat(commandQueue.size()).isEqualTo(3)

        // add loadEvents
        commandQueue.loadEvents(null)
        assertThat(commandQueue.size()).isEqualTo(4)

        // add clearAlarms
        commandQueue.clearAlarms(null)
        assertThat(commandQueue.size()).isEqualTo(5)

        // add deactivate
        commandQueue.deactivate(null)
        assertThat(commandQueue.size()).isEqualTo(6)

        // add updateTime
        commandQueue.updateTime(null)
        assertThat(commandQueue.size()).isEqualTo(7)

        commandQueue.clear()
        commandQueue.tempBasalAbsolute(0.0, 30, true, validProfile, PumpSync.TemporaryBasalType.NORMAL, null)
        commandQueue.pickup()
        assertThat(commandQueue.size()).isEqualTo(0)
        assertThat(commandQueue.performing).isNotNull()
        assertThat(commandQueue.performing?.commandType).isEqualTo(Command.CommandType.TEMPBASAL)
        commandQueue.resetPerforming()
        assertThat(commandQueue.performing).isNull()
    }

    @Test
    fun callingCancelAllBolusesClearsQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)
        val smb = DetailedBolusInfo()
        smb.lastKnownBolusTime = System.currentTimeMillis()
        smb.bolusType = DetailedBolusInfo.BolusType.SMB
        commandQueue.bolus(smb, null)
        commandQueue.bolus(DetailedBolusInfo(), null)
        assertThat(commandQueue.size()).isEqualTo(2)

        // when
        commandQueue.cancelAllBoluses(null)

        // then
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    @Test
    fun smbIsRejectedIfABolusIsQueued() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.bolus(DetailedBolusInfo(), null)
        val smb = DetailedBolusInfo()
        smb.bolusType = DetailedBolusInfo.BolusType.SMB
        val queued: Boolean = commandQueue.bolus(smb, null)

        // then
        assertThat(queued).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun smbIsRejectedIfLastKnownBolusIsOutdated() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        val bolus = DetailedBolusInfo()
        bolus.bolusType = DetailedBolusInfo.BolusType.SMB
        bolus.lastKnownBolusTime = 0
        val queued: Boolean = commandQueue.bolus(bolus, null)

        // then
        assertThat(queued).isFalse()
        assertThat(commandQueue.size()).isEqualTo(0)
    }

    @Test
    fun isCustomCommandRunning() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand2(), null)
        commandQueue.pickup()

        // then
        assertThat(queued1).isTrue()
        assertThat(queued2).isTrue()
        assertThat(commandQueue.isCustomCommandInQueue(CustomCommand1::class.java)).isTrue()
        assertThat(commandQueue.isCustomCommandInQueue(CustomCommand2::class.java)).isTrue()
        assertThat(commandQueue.isCustomCommandInQueue(CustomCommand3::class.java)).isFalse()

        assertThat(commandQueue.isCustomCommandRunning(CustomCommand1::class.java)).isTrue()
        assertThat(commandQueue.isCustomCommandRunning(CustomCommand2::class.java)).isFalse()
        assertThat(commandQueue.isCustomCommandRunning(CustomCommand3::class.java)).isFalse()

        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isSetUserOptionsCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.setUserOptions(null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.SET_USER_SETTINGS)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.setUserOptions(null)
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isLoadEventsCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.loadEvents(null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.LOAD_EVENTS)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.loadEvents(null)
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isClearAlarmsCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.clearAlarms(null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.CLEAR_ALARMS)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.clearAlarms(null)
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isDeactivateCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.deactivate(null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.DEACTIVATE)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.deactivate(null)
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isUpdateTimeCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.updateTime(null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.UPDATE_TIME)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.updateTime(null)
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isLoadTDDsCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.loadTDDs(null)

        // then
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.loadTDDs(null)
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isLoadHistoryCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.loadHistory(0, null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.LOAD_HISTORY)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.loadHistory(0, null)
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isProfileSetCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        testPumpPlugin.isProfileSet = true
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
                assertThat(result.enacted).isFalse()
            }
        })

        // then
        // the same profile -> ignore
        assertThat(commandQueue.size()).isEqualTo(0)
        // different should be added
        testPumpPlugin.isProfileSet = false
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
                assertThat(result.enacted).isTrue()
            }
        })
        assertThat(commandQueue.size()).isEqualTo(1)
        // next should be ignored
        commandQueue.setProfile(validProfile, false, object : Callback() {
            override fun run() {
                assertThat(result.success).isTrue()
            }
        })
        assertThat(commandQueue.size()).isEqualTo(1)
        testPumpPlugin.isProfileSet = true
    }

    @Test
    fun isStopCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.stopPump(null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.STOP_PUMP)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isStarCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.startPump(null)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.START_PUMP)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isSetTbrNotificationCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.setTBROverNotification(null, true)

        // then
        assertThat(commandQueue.isLastScheduled(Command.CommandType.INSIGHT_SET_TBR_OVER_ALARM)).isTrue()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun differentCustomCommandsAllowed() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand2(), null)

        // then
        assertThat(queued1).isTrue()
        assertThat(queued2).isTrue()
        assertThat(commandQueue.size()).isEqualTo(2)
    }

    @Test
    fun sameCustomCommandNotAllowed() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        val queued1 = commandQueue.customCommand(CustomCommand1(), null)
        val queued2 = commandQueue.customCommand(CustomCommand1(), null)

        // then
        assertThat(queued1).isTrue()
        assertThat(queued2).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun readStatusTwiceIsNotAllowed() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        val queued1 = commandQueue.readStatus("1", null)
        val queued2 = commandQueue.readStatus("2", null)

        // then
        assertThat(queued1).isTrue()
        assertThat(queued2).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
        assertThat(commandQueue.statusInQueue()).isTrue()
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
