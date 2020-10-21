package info.nightscout.androidaps.queue

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.Spanned
import androidx.appcompat.app.AppCompatActivity
import dagger.Lazy
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.BolusProgressHelperActivity
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.dialogs.BolusProgressDialog
import info.nightscout.androidaps.events.EventBolusRequested
import info.nightscout.androidaps.events.EventNewBasalProfile
import info.nightscout.androidaps.events.EventProfileNeedsUpdate
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.commands.CustomCommand
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.Notification
import info.nightscout.androidaps.queue.commands.*
import info.nightscout.androidaps.queue.commands.Command.CommandType
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by mike on 08.11.2017.
 *
 *
 * DATA FLOW:
 * ---------
 *
 *
 * (request) - > ConfigBuilder.getCommandQueue().bolus(...)
 *
 *
 * app no longer waits for result but passes Callback
 *
 *
 * request is added to queue, if another request of the same type already exists in queue, it's removed prior adding
 * but if request of the same type is currently executed (probably important only for bolus which is running long time), new request is declined
 * new QueueThread is created and started if current if finished
 * CommandReadStatus is added automatically before command if queue is empty
 *
 *
 * biggest change is we don't need exec pump commands in Handler because it's finished immediately
 * command queueing if not realized by stacking in different Handlers and threads anymore but by internal queue with better control
 *
 *
 * QueueThread calls ConfigBuilder#connect which is passed to getActivePump().connect
 * connect should be executed on background and return immediately. afterwards isConnecting() is expected to be true
 *
 *
 * while isConnecting() == true GUI is updated by posting connection progress
 *
 *
 * if connect is successful: isConnected() becomes true, isConnecting() becomes false
 * CommandQueue starts calling execute() of commands. execute() is expected to be blocking (return after finish).
 * callback with result is called after finish automatically
 * if connect failed: isConnected() becomes false, isConnecting() becomes false
 * connect() is called again
 *
 *
 * when queue is empty, disconnect is called
 */

@Singleton
class CommandQueue @Inject constructor(
    private val injector: HasAndroidInjector,
    val aapsLogger: AAPSLogger,
    val rxBus: RxBusWrapper,
    val resourceHelper: ResourceHelper,
    val constraintChecker: ConstraintChecker,
    val profileFunction: ProfileFunction,
    val activePlugin: Lazy<ActivePluginProvider>,
    val context: Context,
    val sp: SP,
    private val buildHelper: BuildHelper,
    val fabricPrivacy: FabricPrivacy
) : CommandQueueProvider {

    private val disposable = CompositeDisposable()

    private val queue = LinkedList<Command>()
    private var thread: QueueThread? = null

    var performing: Command? = null

    init {
        disposable.add(rxBus
            .toObservable(EventProfileNeedsUpdate::class.java)
            .observeOn(Schedulers.io())
            .subscribe({
                aapsLogger.debug(LTag.PROFILE, "onProfileSwitch")
                profileFunction.getProfile()?.let {
                    setProfile(it, object : Callback() {
                        override fun run() {
                            if (!result.success) {
                                val i = Intent(context, ErrorHelperActivity::class.java)
                                i.putExtra("soundid", R.raw.boluserror)
                                i.putExtra("status", result.comment)
                                i.putExtra("title", resourceHelper.gs(R.string.failedupdatebasalprofile))
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(i)
                            }
                            if (result.enacted) rxBus.send(EventNewBasalProfile())
                        }
                    })
                }
            }) { exception: Throwable -> fabricPrivacy.logException(exception) }
        )

    }

    private fun executingNowError(): PumpEnactResult =
        PumpEnactResult(injector).success(false).enacted(false).comment(resourceHelper.gs(R.string.executingrightnow))

    override fun isRunning(type: CommandType): Boolean = performing?.commandType == type

    @Synchronized
    private fun removeAll(type: CommandType) {
        synchronized(queue) {
            for (i in queue.indices.reversed()) {
                if (queue[i].commandType == type) {
                    queue.removeAt(i)
                }
            }
        }
    }

    @Suppress("SameParameterValue")
    @Synchronized
    private fun isLastScheduled(type: CommandType): Boolean {
        synchronized(queue) {
            if (queue.size > 0 && queue[queue.size - 1].commandType == type) {
                return true
            }
        }
        return false
    }

    @Synchronized
    private fun add(command: Command) {
        aapsLogger.debug(LTag.PUMPQUEUE, "Adding: " + command.javaClass.simpleName + " - " + command.status())
        synchronized(queue) { queue.add(command) }
    }

    @Synchronized
    override fun pickup() {
        synchronized(queue) { performing = queue.poll() }
    }

    @Synchronized
    override fun clear() {
        performing = null
        synchronized(queue) {
            for (i in queue.indices) {
                queue[i].cancel()
            }
            queue.clear()
        }
    }

    override fun size(): Int = queue.size

    override fun performing(): Command? = performing

    override fun resetPerforming() {
        performing = null
    }

    // After new command added to the queue
    // start thread again if not already running
    @Synchronized
    private fun notifyAboutNewCommand() {
        while (thread != null && thread!!.state != Thread.State.TERMINATED && thread!!.waitingForDisconnect) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Waiting for previous thread finish")
            SystemClock.sleep(500)
        }
        if (thread == null || thread!!.state == Thread.State.TERMINATED) {
            thread = QueueThread(this, context, aapsLogger, rxBus, activePlugin.get(), resourceHelper, sp)
            thread!!.start()
            aapsLogger.debug(LTag.PUMPQUEUE, "Starting new thread")
        } else {
            aapsLogger.debug(LTag.PUMPQUEUE, "Thread is already running")
        }
    }

    override fun independentConnect(reason: String, callback: Callback?) {
        aapsLogger.debug(LTag.PUMPQUEUE, "Starting new queue")
        val tempCommandQueue = CommandQueue(injector, aapsLogger, rxBus, resourceHelper, constraintChecker, profileFunction, activePlugin, context, sp, buildHelper, fabricPrivacy)
        tempCommandQueue.readStatus(reason, callback)
    }

    @Synchronized
    override fun bolusInQueue(): Boolean {
        if (isRunning(CommandType.BOLUS)) return true
        synchronized(queue) {
            for (i in queue.indices) {
                if (queue[i].commandType == CommandType.BOLUS) {
                    return true
                }
            }
        }
        return false
    }

    // returns true if command is queued
    @Synchronized
    override fun bolus(detailedBolusInfo: DetailedBolusInfo, callback: Callback?): Boolean {
        var type = if (detailedBolusInfo.isSMB) CommandType.SMB_BOLUS else CommandType.BOLUS
        if (type == CommandType.SMB_BOLUS) {
            if (isRunning(CommandType.BOLUS) || isRunning(CommandType.SMB_BOLUS) || bolusInQueue()) {
                aapsLogger.debug(LTag.PUMPQUEUE, "Rejecting SMB since a bolus is queue/running")
                return false
            }
            if (detailedBolusInfo.lastKnownBolusTime < activePlugin.get().activeTreatments.lastBolusTime) {
                aapsLogger.debug(LTag.PUMPQUEUE, "Rejecting bolus, another bolus was issued since request time")
                return false
            }
            removeAll(CommandType.SMB_BOLUS)
        }
        if (type == CommandType.BOLUS && detailedBolusInfo.carbs > 0 && detailedBolusInfo.insulin == 0.0) {
            type = CommandType.CARBS_ONLY_TREATMENT
            //Carbs only can be added in parallel as they can be "in the future".
        } else {
            if (isRunning(type)) {
                callback?.result(executingNowError())?.run()
                return false
            }
            // remove all unfinished boluses
            removeAll(type)
        }
        // apply constraints
        detailedBolusInfo.insulin = constraintChecker.applyBolusConstraints(Constraint(detailedBolusInfo.insulin)).value()
        detailedBolusInfo.carbs = constraintChecker.applyCarbsConstraints(Constraint(detailedBolusInfo.carbs.toInt())).value().toDouble()
        // add new command to queue
        if (detailedBolusInfo.isSMB) {
            add(CommandSMBBolus(injector, detailedBolusInfo, callback))
        } else {
            add(CommandBolus(injector, detailedBolusInfo, callback, type))
            if (type == CommandType.BOLUS) { // Bring up bolus progress dialog (start here, so the dialog is shown when the bolus is requested,
                // not when the Bolus command is starting. The command closes the dialog upon completion).
                showBolusProgressDialog(detailedBolusInfo.insulin, detailedBolusInfo.context)
                // Notify Wear about upcoming bolus
                rxBus.send(EventBolusRequested(detailedBolusInfo.insulin))
            }
        }
        notifyAboutNewCommand()
        return true
    }

    override fun stopPump(callback: Callback?) {
        add(CommandStopPump(injector, callback))
        notifyAboutNewCommand()
    }

    override fun startPump(callback: Callback?) {
        add(CommandStartPump(injector, callback))
        notifyAboutNewCommand()
    }

    override fun setTBROverNotification(callback: Callback?, enable: Boolean) {
        add(CommandInsightSetTBROverNotification(injector, enable, callback))
        notifyAboutNewCommand()
    }

    @Synchronized
    override fun cancelAllBoluses() {
        if (!isRunning(CommandType.BOLUS)) {
            rxBus.send(EventDismissBolusProgressIfRunning(PumpEnactResult(injector).success(true).enacted(false)))
        }
        removeAll(CommandType.BOLUS)
        removeAll(CommandType.SMB_BOLUS)
        Thread(Runnable { activePlugin.get().activePump.stopBolusDelivering() }).run()
    }

    // returns true if command is queued
    override fun tempBasalAbsolute(absoluteRate: Double, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, callback: Callback?): Boolean {
        if (!enforceNew && isRunning(CommandType.TEMPBASAL)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.TEMPBASAL)
        val rateAfterConstraints = constraintChecker.applyBasalConstraints(Constraint(absoluteRate), profile).value()
        // add new command to queue
        add(CommandTempBasalAbsolute(injector, rateAfterConstraints, durationInMinutes, enforceNew, profile, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun tempBasalPercent(percent: Int, durationInMinutes: Int, enforceNew: Boolean, profile: Profile, callback: Callback?): Boolean {
        if (!enforceNew && isRunning(CommandType.TEMPBASAL)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.TEMPBASAL)
        val percentAfterConstraints = constraintChecker.applyBasalPercentConstraints(Constraint(percent), profile).value()
        // add new command to queue
        add(CommandTempBasalPercent(injector, percentAfterConstraints, durationInMinutes, enforceNew, profile, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun extendedBolus(insulin: Double, durationInMinutes: Int, callback: Callback?): Boolean {
        if (isRunning(CommandType.EXTENDEDBOLUS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        val rateAfterConstraints = constraintChecker.applyExtendedBolusConstraints(Constraint(insulin)).value()
        // remove all unfinished
        removeAll(CommandType.EXTENDEDBOLUS)
        // add new command to queue
        add(CommandExtendedBolus(injector, rateAfterConstraints, durationInMinutes, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun cancelTempBasal(enforceNew: Boolean, callback: Callback?): Boolean {
        if (!enforceNew && isRunning(CommandType.TEMPBASAL)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.TEMPBASAL)
        // add new command to queue
        add(CommandCancelTempBasal(injector, enforceNew, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun cancelExtended(callback: Callback?): Boolean {
        if (isRunning(CommandType.EXTENDEDBOLUS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.EXTENDEDBOLUS)
        // add new command to queue
        add(CommandCancelExtendedBolus(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun setProfile(profile: Profile, callback: Callback?): Boolean {
        if (isThisProfileSet(profile)) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Correct profile already set")
            callback?.result(PumpEnactResult(injector).success(true).enacted(false))?.run()
            return false
        }
        /* this is breaking setting of profile at all if not engineering mode
        if (!buildHelper.isEngineeringModeOrRelease()) {
            val notification = Notification(Notification.NOT_ENG_MODE_OR_RELEASE, resourceHelper.gs(R.string.not_eng_mode_or_release), Notification.URGENT)
            rxBus.send(EventNewNotification(notification))
            callback?.result(PumpEnactResult(injector).success(false).enacted(false).comment(resourceHelper.gs(R.string.not_eng_mode_or_release)))?.run()
            return false
        }
        */
        // Compare with pump limits
        val basalValues = profile.basalValues
        for (basalValue in basalValues) {
            if (basalValue.value < activePlugin.get().activePump.pumpDescription.basalMinimumRate) {
                val notification = Notification(Notification.BASAL_VALUE_BELOW_MINIMUM, resourceHelper.gs(R.string.basalvaluebelowminimum), Notification.URGENT)
                rxBus.send(EventNewNotification(notification))
                callback?.result(PumpEnactResult(injector).success(false).enacted(false).comment(resourceHelper.gs(R.string.basalvaluebelowminimum)))?.run()
                return false
            }
        }
        rxBus.send(EventDismissNotification(Notification.BASAL_VALUE_BELOW_MINIMUM))
        // remove all unfinished
        removeAll(CommandType.BASAL_PROFILE)
        // add new command to queue
        add(CommandSetProfile(injector, profile, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun readStatus(reason: String, callback: Callback?): Boolean {
        if (isLastScheduled(CommandType.READSTATUS)) {
            aapsLogger.debug(LTag.PUMPQUEUE, "READSTATUS $reason ignored as duplicated")
            callback?.result(executingNowError())?.run()
            return false
        }

        // add new command to queue
        add(CommandReadStatus(injector, reason, callback))
        notifyAboutNewCommand()
        return true
    }

    @Synchronized
    override fun statusInQueue(): Boolean {
        if (isRunning(CommandType.READSTATUS)) return true
        synchronized(queue) {
            for (i in queue.indices) {
                if (queue[i].commandType == CommandType.READSTATUS) {
                    return true
                }
            }
        }
        return false
    }

    // returns true if command is queued
    override fun loadHistory(type: Byte, callback: Callback?): Boolean {
        if (isRunning(CommandType.LOAD_HISTORY)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.LOAD_HISTORY)
        // add new command to queue
        add(CommandLoadHistory(injector, type, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun setUserOptions(callback: Callback?): Boolean {
        if (isRunning(CommandType.SET_USER_SETTINGS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.SET_USER_SETTINGS)
        // add new command to queue
        add(CommandSetUserSettings(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun loadTDDs(callback: Callback?): Boolean {
        if (isRunning(CommandType.LOAD_HISTORY)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.LOAD_HISTORY)
        // add new command to queue
        add(CommandLoadTDDs(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    // returns true if command is queued
    override fun loadEvents(callback: Callback?): Boolean {
        if (isRunning(CommandType.LOAD_EVENTS)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAll(CommandType.LOAD_EVENTS)
        // add new command to queue
        add(CommandLoadEvents(injector, callback))
        notifyAboutNewCommand()
        return true
    }

    override fun customCommand(customCommand: CustomCommand, callback: Callback?): Boolean {
        if (isCustomCommandInQueue(customCommand.javaClass)) {
            callback?.result(executingNowError())?.run()
            return false
        }
        // remove all unfinished
        removeAllCustomCommands(customCommand.javaClass)
        // add new command to queue
        add(CommandCustomCommand(injector, customCommand, callback))
        notifyAboutNewCommand()
        return true
    }

    @Synchronized
    override fun isCustomCommandInQueue(customCommandType: Class<out CustomCommand>): Boolean {
        if(isCustomCommandRunning(customCommandType)) {
            return true
        }
        synchronized(queue) {
            for (i in queue.indices) {
                val command = queue[i]
                if (command is CommandCustomCommand && customCommandType.isInstance(command.customCommand)) {
                    return true
                }
            }
        }
        return false
    }

    override fun isCustomCommandRunning(customCommandType: Class<out CustomCommand>): Boolean {
        val performing = this.performing
        if (performing is CommandCustomCommand && customCommandType.isInstance(performing.customCommand)) {
            return true
        }
        return false
    }

    @Synchronized
    private fun removeAllCustomCommands(targetType: Class<out CustomCommand>) {
        synchronized(queue) {
            for (i in queue.indices.reversed()) {
                val command = queue[i]
                if (command is CustomCommand && targetType.isInstance(command.commandType)) {
                    queue.removeAt(i)
                }
            }
        }
    }

    override fun spannedStatus(): Spanned {
        var s = ""
        var line = 0
        val perf = performing
        if (perf != null) {
            s += "<b>" + perf.status() + "</b>"
            line++
        }
        synchronized(queue) {
            for (i in queue.indices) {
                if (line != 0) s += "<br>"
                s += queue[i].status()
                line++
            }
        }
        return HtmlHelper.fromHtml(s)
    }

    override fun isThisProfileSet(profile: Profile): Boolean {
        val activePump = activePlugin.get().activePump
        val current = profileFunction.getProfile()
        return if (current != null) {
            val result = activePump.isThisProfileSet(profile)
            if (!result) {
                aapsLogger.debug(LTag.PUMPQUEUE, "Current profile: $current")
                aapsLogger.debug(LTag.PUMPQUEUE, "New profile: $profile")
            }
            result
        } else true
    }

    private fun showBolusProgressDialog(insulin: Double, ctx: Context?) {
        if (ctx != null) {
            val bolusProgressDialog = BolusProgressDialog()
            bolusProgressDialog.setInsulin(insulin)
            bolusProgressDialog.show((ctx as AppCompatActivity).supportFragmentManager, "BolusProgress")
        } else {
            val i = Intent()
            i.putExtra("insulin", insulin)
            i.setClass(context, BolusProgressHelperActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(i)
        }
    }
}