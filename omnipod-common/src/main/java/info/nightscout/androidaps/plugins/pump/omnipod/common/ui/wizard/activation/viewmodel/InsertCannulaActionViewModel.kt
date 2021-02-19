package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel

import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodManager
import javax.inject.Inject

class InsertCannulaActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager, private val podStateManager: AapsPodStateManager, private val profileFunction: ProfileFunction) : PodActivationActionViewModelBase() {

    override fun isPodInAlarm(): Boolean = podStateManager.isPodFaulted

    override fun isPodActivationTimeExceeded(): Boolean = podStateManager.isPodActivationTimeExceeded

    override fun isPodDeactivatable(): Boolean = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

    override fun doExecuteAction(): PumpEnactResult = aapsOmnipodManager.insertCannula(profileFunction.getProfile())
}