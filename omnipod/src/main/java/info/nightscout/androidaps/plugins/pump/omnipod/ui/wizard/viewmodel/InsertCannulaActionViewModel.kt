package info.nightscout.androidaps.plugins.pump.omnipod.ui.wizard.viewmodel

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.omnipod.manager.AapsOmnipodManager
import javax.inject.Inject

class InsertCannulaActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager, private val profileFunction: ProfileFunction) : ActionViewModelBase() {
    override fun doExecuteAction(): PumpEnactResult = aapsOmnipodManager.insertCannula(profileFunction.getProfile())
}