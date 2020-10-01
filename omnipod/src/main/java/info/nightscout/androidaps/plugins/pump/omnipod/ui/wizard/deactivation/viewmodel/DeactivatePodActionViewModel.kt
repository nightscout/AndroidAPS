package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.deactivation.viewmodel

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.queue.command.CommandDeactivatePod
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.viewmodel.ActionViewModelBase
import info.nightscout.androidaps.queue.Callback
import io.reactivex.subjects.SingleSubject
import javax.inject.Inject

class DeactivatePodActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager, private val commandQueueProvider: CommandQueueProvider) : ActionViewModelBase() {
    override fun doExecuteAction(): PumpEnactResult {
        val singleSubject = SingleSubject.create<PumpEnactResult>()
        commandQueueProvider.customCommand(CommandDeactivatePod(), object : Callback() {
            override fun run() {
                singleSubject.onSuccess(result)
            }
        })
        return singleSubject.blockingGet()
    }
}