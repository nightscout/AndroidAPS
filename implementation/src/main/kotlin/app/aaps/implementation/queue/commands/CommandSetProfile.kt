package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import dagger.android.HasAndroidInjector
import javax.inject.Inject
import javax.inject.Provider

class CommandSetProfile(
    injector: HasAndroidInjector,
    private val profile: Profile,
    private val hasNsId: Boolean,
    override val callback: Callback?,
) : Command {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var smsCommunicator: SmsCommunicator
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var config: Config
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var pumpEnactResultProvider: Provider<PumpEnactResult>

    init {
        injector.androidInjector().inject(this)
    }

    override val commandType: Command.CommandType = Command.CommandType.BASAL_PROFILE

    override fun execute() {
        if (commandQueue.isThisProfileSet(profile) && persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now()) != null) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Correct profile already set. profile: $profile")
            callback?.result(pumpEnactResultProvider.get().success(true).enacted(false))?.run()
            return
        }
        val r = activePlugin.activePump.setNewBasalProfile(profile)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted} profile: $profile")
        callback?.result(r)?.run()
        // Send SMS notification if ProfileSwitch is coming from NS
        val profileSwitch = persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())
        if (profileSwitch != null && r.enacted && hasNsId && !config.AAPSCLIENT) {
            if (smsCommunicator.isEnabled() && !config.doNotSendSmsOnProfileChange())
                smsCommunicator.sendNotificationToAllNumbers(rh.gs(app.aaps.core.ui.R.string.profile_set_ok))
        }
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.set_profile)

    override fun log(): String = "SET PROFILE"
    override fun cancel() {
        aapsLogger.debug(LTag.PUMPQUEUE, "Result cancel")
        callback?.result(pumpEnactResultProvider.get().success(false).comment(app.aaps.core.ui.R.string.connectiontimedout))?.run()
    }
}