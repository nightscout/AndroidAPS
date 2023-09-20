package info.nightscout.implementation.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.database.ValueWrapper
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.Profile
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.Command
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.smsCommunicator.SmsCommunicator
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.utils.DateUtil
import javax.inject.Inject

class CommandSetProfile constructor(
    injector: HasAndroidInjector,
    private val profile: Profile,
    private val hasNsId: Boolean,
    callback: Callback?
) : Command(injector, CommandType.BASAL_PROFILE, callback) {

    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var config: Config
    @Inject lateinit var repository: AppRepository

    override fun execute() {
        if (commandQueue.isThisProfileSet(profile) && repository.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Correct profile already set. profile: $profile")
            callback?.result(PumpEnactResult(injector).success(true).enacted(false))?.run()
            return
        }
        val r = activePlugin.activePump.setNewBasalProfile(profile)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted} profile: $profile")
        callback?.result(r)?.run()
        // Send SMS notification if ProfileSwitch is coming from NS
        val profileSwitch = repository.getEffectiveProfileSwitchActiveAt(dateUtil.now()).blockingGet()
        if (profileSwitch is ValueWrapper.Existing && r.enacted && hasNsId && !config.NSCLIENT) {
            if (smsCommunicator.isEnabled())
                smsCommunicator.sendNotificationToAllNumbers(rh.gs(info.nightscout.core.ui.R.string.profile_set_ok))
        }
    }

    override fun status(): String = rh.gs(info.nightscout.core.ui.R.string.set_profile)

    override fun log(): String = "SET PROFILE"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(info.nightscout.core.ui.R.string.connectiontimedout))?.run()
    }
}