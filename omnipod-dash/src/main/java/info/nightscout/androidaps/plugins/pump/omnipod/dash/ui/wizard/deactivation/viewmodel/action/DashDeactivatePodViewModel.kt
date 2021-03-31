package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.deactivation.viewmodel.action

import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.common.R
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.deactivation.viewmodel.action.DeactivatePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

class DashDeactivatePodViewModel @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    private val podStateManager: OmnipodDashPodStateManager,
    injector: HasAndroidInjector,
    logger: AAPSLogger
) : DeactivatePodViewModel(injector, logger) {

    override fun doExecuteAction(): Single<PumpEnactResult> = Single.create { source ->
        omnipodManager.deactivatePod().subscribeBy(
            onNext = { podEvent ->
                logger.debug(
                    LTag.PUMP,
                    "Received PodEvent in Pod deactivation: $podEvent"
                )
            },
            onError = { throwable ->
                logger.error(LTag.PUMP, "Error in Pod deactivation", throwable)
                source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.message))
            },
            onComplete = {
                logger.debug("Pod deactivation completed")
                source.onSuccess(PumpEnactResult(injector).success(true))
            }
        )
    }

    override fun discardPod() {
        podStateManager.reset()
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_title

    @StringRes
    override fun getTextId(): Int = R.string.omnipod_common_pod_deactivation_wizard_deactivating_pod_text
}
