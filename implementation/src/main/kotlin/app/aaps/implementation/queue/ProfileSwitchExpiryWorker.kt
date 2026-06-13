package app.aaps.implementation.queue

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.WorkerParameters
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.queue.Command
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventProfileChangeRequested
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.workflow.LoggingWorker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers

/**
 * Scheduled to fire when a timed profile switch ends.
 *
 * Expiry of a timed profile switch is passive: the ProfileSwitch record simply stops matching
 * the "active" query while the loop and the UI keep using the last EffectiveProfileSwitch with
 * the old percentage/timeshift. Without this worker the revert depends solely on
 * KeepAliveWorker, which can be delayed indefinitely by Doze.
 */
@HiltWorker
class ProfileSwitchExpiryWorker @AssistedInject internal constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    aapsLogger: AAPSLogger,
    fabricPrivacy: FabricPrivacy,
    private val config: Config,
    private val profileFunction: ProfileFunction,
    private val commandQueue: CommandQueue,
    private val rxBus: RxBus
) : LoggingWorker(context, params, Dispatchers.Default, aapsLogger, fabricPrivacy) {

    companion object {

        const val WORK_NAME = "ProfileSwitchExpiry"
    }

    override suspend fun doWorkAndLog(): Result {
        if (config.AAPSCLIENT) return Result.success()
        if (profileFunction.isProfileChangePending() && !commandQueue.isRunning(Command.CommandType.BASAL_PROFILE)) {
            aapsLogger.debug(LTag.PROFILE, "Timed profile switch expired. Requesting profile update")
            rxBus.send(EventProfileChangeRequested())
        }
        return Result.success()
    }
}
