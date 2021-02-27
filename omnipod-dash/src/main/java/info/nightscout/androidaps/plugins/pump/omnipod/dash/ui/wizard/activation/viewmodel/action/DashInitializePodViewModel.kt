package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import javax.inject.Inject

class DashInitializePodViewModel @Inject constructor(private val aapsLogger: AAPSLogger,
                                                     private val injector: HasAndroidInjector,
                                                     private val omnipodManager: OmnipodDashManager) : InitializePodViewModel() {

    override fun isPodInAlarm(): Boolean = false // TODO

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO

    override fun isPodDeactivatable(): Boolean = true // TODO

    override fun doExecuteAction(): PumpEnactResult {
        // TODO FIRST STEP OF ACTIVATION
        val disposable = omnipodManager.activatePodPart1().subscribe(
            { podEvent -> aapsLogger.debug(LTag.PUMP, "Received PodEvent in Pod activation part 1: $podEvent") },
            { throwable -> aapsLogger.error(LTag.PUMP, "Error in Pod activation part 1: $throwable") },
            { aapsLogger.debug("Pod activation part 1 completed") }
        )

        return PumpEnactResult(injector).success(false).comment("not implemented")
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_initialize_pod_title

    @StringRes
    override fun getTextId() = R.string.omnipod_dash_pod_activation_wizard_initialize_pod_text
}
