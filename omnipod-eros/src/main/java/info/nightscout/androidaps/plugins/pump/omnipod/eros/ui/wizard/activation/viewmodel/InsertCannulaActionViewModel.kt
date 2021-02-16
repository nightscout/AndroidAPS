package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.common.viewmodel.ActionViewModelBase
import javax.inject.Inject

class InsertCannulaActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager, private val profileFunction: ProfileFunction) : ActionViewModelBase() {

    override fun doExecuteAction(): PumpEnactResult = aapsOmnipodManager.insertCannula(profileFunction.getProfile())
}