package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.viewmodel

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import javax.inject.Inject

class PairAndPrimePodActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager) : ActionViewModelBase() {
    override fun doExecuteAction(): PumpEnactResult = aapsOmnipodManager.activateNewPod()
}