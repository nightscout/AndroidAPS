package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.common.viewmodel.ActionViewModelBase
import javax.inject.Inject

class InitializePodActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager) : ActionViewModelBase() {

    override fun doExecuteAction(): PumpEnactResult = aapsOmnipodManager.initializePod()
}