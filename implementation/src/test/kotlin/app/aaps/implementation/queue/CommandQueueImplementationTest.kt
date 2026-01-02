package app.aaps.implementation.queue

import android.content.Context
import android.os.Handler
import android.os.PowerManager
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CustomCommand
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.implementation.queue.commands.CommandBolus
import app.aaps.implementation.queue.commands.CommandCancelExtendedBolus
import app.aaps.implementation.queue.commands.CommandCancelTempBasal
import app.aaps.implementation.queue.commands.CommandClearAlarms
import app.aaps.implementation.queue.commands.CommandCustomCommand
import app.aaps.implementation.queue.commands.CommandDeactivate
import app.aaps.implementation.queue.commands.CommandExtendedBolus
import app.aaps.implementation.queue.commands.CommandLoadEvents
import app.aaps.implementation.queue.commands.CommandLoadHistory
import app.aaps.implementation.queue.commands.CommandReadStatus
import app.aaps.implementation.queue.commands.CommandSMBBolus
import app.aaps.implementation.queue.commands.CommandTempBasalPercent
import app.aaps.implementation.queue.commands.CommandUpdateTime
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.ListenableFuture
import dagger.android.HasAndroidInjector
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Calendar
import javax.inject.Provider

class CommandQueueImplementationTest : TestBaseWithProfile() {

    @Mock lateinit var constraintChecker: ConstraintsChecker
    @Mock lateinit var powerManager: PowerManager
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var androidPermission: AndroidPermission
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var jobName: CommandQueueName
    @Mock lateinit var workManager: WorkManager
    @Mock lateinit var infos: ListenableFuture<List<WorkInfo>>

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
        config: Config,
        dateUtil: DateUtil,
        fabricPrivacy: FabricPrivacy,
        uiInteraction: UiInteraction,
        persistenceLayer: PersistenceLayer,
        decimalFormatter: DecimalFormatter,
        pumpEnactResultProvider: Provider<PumpEnactResult>,
        jobName: CommandQueueName,
        workManager: WorkManager
    ) : CommandQueueImplementation(
        injector, aapsLogger, rxBus, aapsSchedulers, rh, constraintChecker, profileFunction,
        activePlugin, context, config, dateUtil, fabricPrivacy,
        uiInteraction, persistenceLayer, decimalFormatter, pumpEnactResultProvider, jobName, workManager
    ) {

        override fun notifyAboutNewCommand(): Boolean = true

    }

    init {
        addInjector {
            if (it is CommandCancelExtendedBolus) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandTempBasalPercent) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandCancelTempBasal) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandBolus) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
                it.rxBus = rxBus
            }
            if (it is CommandSMBBolus) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandCustomCommand) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandExtendedBolus) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandLoadHistory) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandLoadEvents) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandReadStatus) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandClearAlarms) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandDeactivate) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is CommandUpdateTime) {
                it.aapsLogger = aapsLogger
                it.rh = rh
                it.activePlugin = activePlugin
            }
            if (it is QueueWorker) {
                it.aapsLogger = aapsLogger
                it.queue = commandQueue
                it.context = context
                it.rxBus = rxBus
                it.activePlugin = activePlugin
                it.rh = rh
                it.preferences = preferences
                it.androidPermission = androidPermission
                it.config = config
            }
        }
    }

    private lateinit var commandQueue: CommandQueueImplementation

    @BeforeEach
    fun prepare() {
        commandQueue = CommandQueueMocked(
            injector, aapsLogger, rxBus, aapsSchedulers, rh, constraintChecker, profileFunction, activePlugin, context,
            config, dateUtil, fabricPrivacy, uiInteraction, persistenceLayer, decimalFormatter, pumpEnactResultProvider, jobName, workManager
        )
        testPumpPlugin.pumpDescription.basalMinimumRate = 0.1
        testPumpPlugin.connected = true

        whenever(context.getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager)
        whenever(activePlugin.activePump).thenReturn(testPumpPlugin)
        whenever(persistenceLayer.getEffectiveProfileSwitchActiveAt(anyLong())).thenReturn(effectiveProfileSwitch)
        whenever(persistenceLayer.getNewestBolus()).thenReturn(
            BS(
                timestamp = Calendar.getInstance().also { it.set(2000, 0, 1) }.timeInMillis,
                type = BS.Type.NORMAL,
                amount = 0.0
            )
        )
        whenever(profileFunction.getProfile()).thenReturn(validProfile)

        val bolusConstraint = ConstraintObject(0.0, aapsLogger)
        whenever(constraintChecker.applyBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
        whenever(constraintChecker.applyExtendedBolusConstraints(anyOrNull())).thenReturn(bolusConstraint)
        val carbsConstraint = ConstraintObject(0, aapsLogger)
        whenever(constraintChecker.applyCarbsConstraints(anyOrNull())).thenReturn(carbsConstraint)
        val rateConstraint = ConstraintObject(0.0, aapsLogger)
        whenever(constraintChecker.applyBasalConstraints(anyOrNull(), anyOrNull())).thenReturn(rateConstraint)
        val percentageConstraint = ConstraintObject(0, aapsLogger)
        whenever(constraintChecker.applyBasalPercentConstraints(anyOrNull(), anyOrNull())).thenReturn(percentageConstraint)
        whenever(rh.gs(app.aaps.core.ui.R.string.connectiontimedout)).thenReturn("Connection timed out")
        whenever(rh.gs(app.aaps.core.ui.R.string.format_insulin_units)).thenReturn("%1\$.2f U")
        whenever(rh.gs(app.aaps.core.ui.R.string.goingtodeliver)).thenReturn("Going to deliver %1\$.2f U")
        whenever(workManager.getWorkInfosForUniqueWork(anyOrNull())).thenReturn(infos)
        doAnswer { invocation: InvocationOnMock ->
            Thread {
                val work = TestListenableWorkerBuilder<QueueWorker>(context).build()
                runBlocking { work.doWorkAndLog() }
            }.start()
            null
        }.whenever(workManager).enqueueUniqueWork(anyOrNull(), anyOrNull(), any<OneTimeWorkRequest>())
        whenever(infos.get()).thenReturn(emptyList())
    }

    @Test
    fun commandIsPickedUp() {
        commandQueue = CommandQueueImplementation(
            injector, aapsLogger, rxBus, aapsSchedulers, rh,
            constraintChecker, profileFunction, activePlugin, context,
            config, dateUtil, fabricPrivacy, uiInteraction, persistenceLayer, decimalFormatter, pumpEnactResultProvider, jobName, workManager
        )
        val handler: Handler = mock()
        whenever(handler.post(anyOrNull())).thenAnswer { invocation: InvocationOnMock ->
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
        Thread.sleep(3000)

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
        commandQueue.cancelTempBasal(enforceNew = false, autoForced = false, callback = null)
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
        smb.bolusType = BS.Type.SMB
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
        smb.bolusType = BS.Type.SMB
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
        bolus.bolusType = BS.Type.SMB
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
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
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
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
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
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
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
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
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
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
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
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
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
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isStarCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.startPump(null)

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
        assertThat(commandQueue.size()).isEqualTo(1)
    }

    @Test
    fun isSetTbrNotificationCommandInQueue() {
        // given
        assertThat(commandQueue.size()).isEqualTo(0)

        // when
        commandQueue.setTBROverNotification(null, true)

        // then
        assertThat(commandQueue.isReadStatusScheduled()).isFalse()
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
