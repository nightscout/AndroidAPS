package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.activation.viewmodel

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.common.viewmodel.ActionViewModelBase
import javax.inject.Inject

class InitializePodActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager) : ActionViewModelBase() {

    override fun doExecuteAction(): PumpEnactResult = aapsOmnipodManager.initializePod()
}