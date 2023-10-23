package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.database.ValueWrapper
import app.aaps.database.impl.AppRepository
import dagger.android.HasAndroidInjector
import javax.inject.Inject

class CommandSetProfile(
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
                smsCommunicator.sendNotificationToAllNumbers(rh.gs(app.aaps.core.ui.R.string.profile_set_ok))
        }
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.set_profile)

    override fun log(): String = "SET PROFILE"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(PumpEnactResult(injector).success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}