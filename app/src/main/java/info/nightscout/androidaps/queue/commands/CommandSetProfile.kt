package info.nightscout.androidaps.queue.commands

import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.smsCommunicator.SmsCommunicatorPlugin
import info.nightscout.androidaps.queue.Callback
import javax.inject.Inject

class CommandSetProfile constructor(
    injector: HasAndroidInjector,
    private val profile: Profile,
    callback: Callback?
) : Command(injector, CommandType.BASAL_PROFILE, callback) {

    @Inject lateinit var smsCommunicatorPlugin: SmsCommunicatorPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider

    override fun execute() {
        if (commandQueue.isThisProfileSet(profile)) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Correct profile already set. profile: $profile")
            callback?.result(PumpEnactResult(injector).success(true).enacted(false))?.run()
            return
        }
        val r = activePlugin.activePump.setNewBasalProfile(profile)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted} profile: $profile")
        callback?.result(r)?.run()
        // Send SMS notification if ProfileSwitch is coming from NS
        val profileSwitch = activePlugin.activeTreatments.getProfileSwitchFromHistory(System.currentTimeMillis())
        if (profileSwitch != null && r.enacted && profileSwitch.source == Source.NIGHTSCOUT) {
            if (smsCommunicatorPlugin.isEnabled(PluginType.GENERAL)) {
                smsCommunicatorPlugin.sendNotificationToAllNumbers(resourceHelper.gs(R.string.profile_set_ok))
            }
        }
    }

    override fun status(): String = "SET PROFILE"
}