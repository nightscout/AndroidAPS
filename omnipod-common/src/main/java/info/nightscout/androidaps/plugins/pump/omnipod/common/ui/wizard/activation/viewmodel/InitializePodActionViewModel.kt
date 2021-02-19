package info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel

import androidx.annotation.StringRes
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodManager
import javax.inject.Inject

class InitializePodActionViewModel @Inject constructor(private val aapsOmnipodManager: AapsOmnipodManager, private val podStateManager: AapsPodStateManager) : PodActivationActionViewModelBase() {

    override fun isPodInAlarm(): Boolean = podStateManager.isPodFaulted

    override fun isPodActivationTimeExceeded(): Boolean = podStateManager.isPodActivationTimeExceeded

    override fun isPodDeactivatable(): Boolean = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

    override fun doExecuteAction(): PumpEnactResult = aapsOmnipodManager.initializePod()

    @StringRes fun getTextId() = R.string.omnipod_pod_activation_wizard_initialize_pod_text
}