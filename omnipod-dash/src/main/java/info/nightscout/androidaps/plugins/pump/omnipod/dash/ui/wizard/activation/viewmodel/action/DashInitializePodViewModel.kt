package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import io.reactivex.Single
import javax.inject.Inject

class DashInitializePodViewModel @Inject constructor(private val omnipodManager: OmnipodDashManager) : InitializePodViewModel() {

    override fun isPodInAlarm(): Boolean = false // TODO

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO

    override fun isPodDeactivatable(): Boolean = true // TODO

    override fun doExecuteAction(): Single<PumpEnactResult> =
        Single.create { source ->
            val disposable = omnipodManager.activatePodPart1().subscribe(
                { podEvent -> logger.debug(LTag.PUMP, "Received PodEvent in Pod activation part 1: $podEvent") },
                { throwable ->
                    logger.error(LTag.PUMP, "Error in Pod activation part 1", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.message))
                },
                {
                    logger.debug("Pod activation part 1 completed")
                    source.onSuccess(PumpEnactResult(injector).success(true))
                }
            )
        }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_initialize_pod_title

    @StringRes
    override fun getTextId() = R.string.omnipod_dash_pod_activation_wizard_initialize_pod_text
}
