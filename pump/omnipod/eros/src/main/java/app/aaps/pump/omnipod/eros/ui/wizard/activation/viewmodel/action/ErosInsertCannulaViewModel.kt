package app.aaps.pump.omnipod.eros.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import app.aaps.pump.omnipod.eros.driver.definition.ActivationProgress
import app.aaps.pump.omnipod.eros.manager.AapsErosPodStateManager
import app.aaps.pump.omnipod.eros.manager.AapsOmnipodErosManager
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject
import javax.inject.Provider

class ErosInsertCannulaViewModel @Inject constructor(
    private val aapsOmnipodManager: AapsOmnipodErosManager,
    private val podStateManager: AapsErosPodStateManager,
    private val profileFunction: ProfileFunction,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : InsertCannulaViewModel(pumpEnactResultProvider, logger, aapsSchedulers) {

    override fun isPodInAlarm(): Boolean = podStateManager.isPodFaulted

    override fun isPodActivationTimeExceeded(): Boolean = podStateManager.isPodActivationTimeExceeded

    override fun isPodDeactivatable(): Boolean = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

    override fun doExecuteAction(): Single<PumpEnactResult> = Single.fromCallable { aapsOmnipodManager.insertCannula(profileFunction.getProfile()) }

    @StringRes
    override fun getTitleId(): Int = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_insert_cannula_title

    @StringRes
    override fun getTextId() = app.aaps.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_insert_cannula_text
}