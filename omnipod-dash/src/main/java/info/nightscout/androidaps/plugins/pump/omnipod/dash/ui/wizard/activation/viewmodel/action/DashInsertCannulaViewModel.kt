package info.nightscout.androidaps.plugins.pump.omnipod.dash.ui.wizard.activation.viewmodel.action

import androidx.annotation.StringRes
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.pump.omnipod.common.ui.wizard.activation.viewmodel.action.InsertCannulaViewModel
import info.nightscout.androidaps.plugins.pump.omnipod.dash.R
import io.reactivex.Single
import javax.inject.Inject

class DashInsertCannulaViewModel @Inject constructor(private val profileFunction: ProfileFunction) : InsertCannulaViewModel() {

    override fun isPodInAlarm(): Boolean = false // TODO

    override fun isPodActivationTimeExceeded(): Boolean = false // TODO

    override fun isPodDeactivatable(): Boolean = true // TODO

    override fun doExecuteAction(): Single<PumpEnactResult> = Single.just(PumpEnactResult(injector).success(false).comment("TODO")) // TODO

    @StringRes
    override fun getTitleId(): Int = R.string.omnipod_common_pod_activation_wizard_insert_cannula_title

    @StringRes
    override fun getTextId() = R.string.omnipod_common_pod_activation_wizard_insert_cannula_text
}