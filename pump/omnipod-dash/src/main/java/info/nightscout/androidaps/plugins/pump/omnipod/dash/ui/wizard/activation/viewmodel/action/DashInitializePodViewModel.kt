package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.definition.OmnipodCommandType
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.OmnipodDashManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.definition.AlertTrigger
import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.state.OmnipodDashPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.DashHistory
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.InitialResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.history.data.ResolvedResult
import info.nightscout.androidaps.plugins.pump.omnipod.dash.util.I8n
import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import javax.inject.Inject

class DashInitializePodViewModel @Inject constructor(
    private val omnipodManager: OmnipodDashManager,
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    private val sp: SP,
    private val podStateManager: OmnipodDashPodStateManager,
    private val rh: ResourceHelper,
    private val history: DashHistory,
    aapsSchedulers: AapsSchedulers

) : InitializePodViewModel(injector, logger, aapsSchedulers) {

    override fun isPodInAlarm(): Boolean = false // TODO

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO

    override fun isPodDeactivatable(): Boolean = true // TODO

    override fun doExecuteAction(): Single<PumpEnactResult> =
        Single.create { source ->
            val lowReservoirAlertEnabled = sp.getBoolean(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.key_omnipod_common_low_reservoir_alert_enabled, true)
            val lowReservoirAlertUnits = sp.getInt(info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.key_omnipod_common_low_reservoir_alert_units, 10)
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
                            PumpEnactResult(injector)
                                .success(false)
                                .comment(I8n.textFromException(throwable, rh))
                        )
                    },
                    onComplete = {
                        logger.debug("Pod activation part 1 completed")
                        source.onSuccess(PumpEnactResult(injector).success(true))
                    }
                )
        }

    @StringRes
    override fun getTitleId(): Int = info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_initialize_pod_title

    @StringRes
    override fun getTextId() = R.string.omnipod_dash_pod_activation_wizard_initialize_pod_text
}
