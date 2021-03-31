package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.mapProfileToBasalProgram
import io.reactivex.Single
import io.reactivex.rxkotlin.subscribeBy
import javax.inject.Inject

class DashInsertCannulaViewModel @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    private val profileFunction: ProfileFunction,
    injector: HasAndroidInjector,
    logger: AAPSLogger
) : InsertCannulaViewModel(injector, logger) {

    override fun isPodInAlarm(): Boolean = false // TODO

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO

    override fun isPodDeactivatable(): Boolean = true // TODO

    override fun doExecuteAction(): Single<PumpEnactResult> = Single.create { source ->
        val profile = profileFunction.getProfile()
        if (profile == null) {
            source.onError(IllegalStateException("No profile set"))
        } else {
            val basalProgram = mapProfileToBasalProgram(profile)
            logger.debug(
                LTag.PUMPCOMM,
                "Mapped profile to basal program. profile={}, basalProgram={}",
                profile,
                basalProgram
            )
            val disposable = omnipodManager.activatePodPart2(basalProgram).subscribeBy(
                onNext = { podEvent ->
                    logger.debug(
                        LTag.PUMP,
                        "Received PodEvent in Pod activation part 2: $podEvent"
                    )
                },
                onError = { throwable ->
                    logger.error(LTag.PUMP, "Error in Pod activation part 2", throwable)
                    source.onSuccess(PumpEnactResult(injector).success(false).comment(throwable.message))
                },
                onComplete = {
                    logger.debug("Pod activation part 2 completed")
                    source.onSuccess(PumpEnactResult(injector).success(true))
                }
            )
        }
    }

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_insert_cannula_title

    @StringRes
    override fun getTextId() = R.string.omnipod_common_pod_activation_wizard_insert_cannula_text
}
