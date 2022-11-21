package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.action

import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandDeactivatePod
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class ErosDeactivatePodViewModel @Inject constructor(
    private val aapsOmnipodManager: AapsOmnipodErosManager,
    private val commandQueue: CommandQueue,
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : DeactivatePodViewModel(injector, logger, aapsSchedulers) {

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