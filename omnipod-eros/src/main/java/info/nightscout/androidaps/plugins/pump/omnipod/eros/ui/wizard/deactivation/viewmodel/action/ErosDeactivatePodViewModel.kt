package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.deactivation.viewmodel.action

import androidx.annotation.StringRes
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.queue.command.CommandDeactivatePod
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import info.nightscout.androidaps.queue.Callback
import io.reactivex.subjects.SingleSubject
import javax.inject.Inject

class ErosDeactivatePodViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodErosManager, private val commandQueueProvider: CommandQueueProvider) : DeactivatePodViewModel() {

    override fun doExecuteAction(): PumpEnactResult {
        val singleSubject = SingleSubject.create<PumpEnactResult>()
        commandQueueProvider.customCommand(CommandDeactivatePod(), object : Callback() {
            override fun run() {
                singleSubject.onSuccess(result)
            }
        })
        return singleSubject.blockingGet()
    }

    override fun discardPod() {
        aapsOmnipodManager.discardPodState()
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_text
}