package app.aaps.implementation.queue.commands

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.ExternalOptions
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.EffectiveProfile
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.smsCommunicator.SmsCommunicator
import app.aaps.core.interfaces.utils.DateUtil
import javax.inject.Provider

class CommandSetProfile(
    private val aapsLogger: AAPSLogger,
    private val rh: ResourceHelper,
    private val smsCommunicator: SmsCommunicator,
    private val activePlugin: ActivePlugin,
    private val dateUtil: DateUtil,
    private val commandQueue: CommandQueue,
    private val config: Config,
    private val persistenceLayer: PersistenceLayer,
    override val pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val profile: EffectiveProfile,
    private val hasNsId: Boolean,
    override val callback: Callback?,
) : Command {

    override val commandType: Command.CommandType = Command.CommandType.BASAL_PROFILE

    override suspend fun execute(): PumpEnactResult {
        if (commandQueue.isThisProfileSet(profile) && persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now()) != null) {
            aapsLogger.debug(LTag.PUMPQUEUE, "Correct profile already set. profile: $profile")
            return pumpEnactResultProvider.get().success(true).enacted(false)
        }
        val r = activePlugin.activePump.setNewBasalProfile(profile)
        aapsLogger.debug(LTag.PUMPQUEUE, "Result success: ${r.success} enacted: ${r.enacted} profile: $profile")
        // Send SMS notification if ProfileSwitch is coming from NS
        val profileSwitch = persistenceLayer.getEffectiveProfileSwitchActiveAt(dateUtil.now())
        if (profileSwitch != null && r.enacted && hasNsId && !config.AAPSCLIENT) {
            if (smsCommunicator.isEnabled() && !config.isEnabled(ExternalOptions.DO_NOT_SEND_SMS_ON_PROFILE_CHANGE))
                smsCommunicator.sendNotificationToAllNumbers(rh.gs(app.aaps.core.ui.R.string.profile_set_ok))
        }
        return r
    }

    override fun status(): String = rh.gs(app.aaps.core.ui.R.string.set_profile)

    override fun log(): String = "SET PROFILE"
}
