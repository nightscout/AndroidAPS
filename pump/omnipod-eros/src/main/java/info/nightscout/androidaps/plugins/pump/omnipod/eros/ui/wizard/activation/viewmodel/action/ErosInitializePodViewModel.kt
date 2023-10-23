package info.nightscout.androidaps.plugins.pump.omnipod.eros.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.rx.AapsSchedulers
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InitializePodViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.eros.R
import info.nightscout.androidaps.plugins.pump.omnipod.eros.driver.definition.ActivationProgress
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsErosPodStateManager
import info.nightscout.androidaps.plugins.pump.omnipod.eros.manager.AapsOmnipodErosManager
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class ErosInitializePodViewModel @Inject constructor(
    private val aapsOmnipodManager: AapsOmnipodErosManager,
    private val podStateManager: AapsErosPodStateManager,
    injector: HasAndroidInjector,
    logger: AAPSLogger,
    aapsSchedulers: AapsSchedulers
) : InitializePodViewModel(injector, logger, aapsSchedulers) {

    override fun isPodInAlarm(): Boolean = podStateManager.isPodFaulted

    override fun isPodActivationTimeExceeded(): Boolean = podStateManager.isPodActivationTimeExceeded

    override fun isPodDeactivatable(): Boolean = podStateManager.activationProgress.isAtLeast(ActivationProgress.PAIRING_COMPLETED)

    override fun doExecuteAction(): Single<PumpEnactResult> = Single.fromCallable { aapsOmnipodManager.initializePod() }

    @StringRes
    override fun getTitleId(): Int = info.nightscout.androidaps.plugins.pump.omnipod.common.R.string.omnipod_common_pod_activation_wizard_initialize_pod_title

    @StringRes
    override fun getTextId() = R.string.omnipod_eros_pod_activation_wizard_initialize_pod_text
}