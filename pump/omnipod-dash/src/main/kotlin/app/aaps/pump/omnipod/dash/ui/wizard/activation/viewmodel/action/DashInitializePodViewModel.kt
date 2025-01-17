package app.aaps.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.objects.Instantiator
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.omnipod.common.definition.OmnipodCommandType
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import app.aaps.pump.omnipod.dash.R
import app.aaps.pump.omnipod.dash.driver.OmnipodDashManager
import app.aaps.pump.omnipod.dash.driver.pod.definition.AlertTrigger
import app.aaps.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import app.aaps.pump.omnipod.dash.history.DashHistory
import app.aaps.pump.omnipod.dash.history.data.InitialResult
import app.aaps.pump.omnipod.dash.history.data.ResolvedResult
import app.aaps.pump.omnipod.dash.util.I8n
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class DashInitializePodViewModel @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    instantiator: Instantiator,
    logger: AAPSLogger,
    private val sp: SP,
    private val podStateManager: OmnipodDashPodStateManager,
    private val rh: ResourceHelper,
    private val history: DashHistory,
    aapsSchedulers: AapsSchedulers

) : InitializePodViewModel(instantiator, logger, aapsSchedulers) {

    override fun isPodInAlarm(): Boolean = false // TODO

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO

    override fun isPodDeactivatable(): Boolean = true // TODO

    override fun doExecuteAction(): Single<PumpEnactResult> =
        Single.create { source ->
            val lowReservoirAlertEnabled = sp.getBoolean(app.aaps.pump.omnipod.common.R.string.key_omnipod_common_low_reservoir_alert_enabled, true)
            val lowReservoirAlertUnits = sp.getInt(app.aaps.pump.omnipod.common.R.string.key_omnipod_common_low_reservoir_alert_units, 10)
            val lowReservoirAlertTrigger = if (lowReservoirAlertEnabled) {
                AlertTrigger.ReservoirVolumeTrigger((lowReservoirAlertUnits * 10).toShort())
            } else
                null

            super.disposable += omnipodManager.activatePodPart1(lowReservoirAlertTrigger)
                .ignoreElements()
                .andThen(podStateManager.updateLowReservoirAlertSettings(lowReservoirAlertEnabled, lowReservoirAlertUnits))
                .andThen(
                    history.createRecord(
                        OmnipodCommandType.INITIALIZE_POD,
                        initialResult = InitialResult.SENT,
                        resolveResult = ResolvedResult.SUCCESS,
                        resolvedAt = System.currentTimeMillis(),
                    ).ignoreElement()
                )
                .subscribeBy(
                    onError = { throwable ->
                        logger.error(LTag.PUMP, "Error in Pod activation part 1", throwable)
                        source.onSuccess(
                            instantiator.providePumpEnactResult()
                                .success(false)
                                .comment(I8n.textFromException(throwable, rh))
                        )
                    },
                    onComplete = {
                        logger.debug("Pod activation part 1 completed")
                        source.onSuccess(instantiator.providePumpEnactResult().success(true))
                    }
                )
        }

    @StringRes
    override fun getTitleId(): Int = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_initialize_pod_title

    @StringRes
    override fun getTextId() = R.string.omnipod_dash_pod_activation_wizard_initialize_pod_text
}
