package app.aaps.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.action

import androidx.annotation.StringRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.common.R
import app.aaps.pump.omnipod.common.queue.command.CommandDeactivatePod
import app.aaps.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import javax.inject.Provider

class ErosDeactivatePodViewModel @Inject constructor(
    private val aapsOmnipodManager: AapsOmnipodErosManager,
    private val commandQueue: CommandQueue,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : DeactivatePodViewModel(pumpEnactResultProvider, logger, aapsSchedulers) {

    override fun doExecuteAction(): Single<PumpEnactResult> =
        Single.create { source ->
            commandQueue.customCommand(CommandDeactivatePod(), object : Callback() {
                override fun run() {
                    source.onSuccess(result)
                }
            })
        }

    override fun discardPod() {
        aapsOmnipodManager.discardPodState()
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_text
}