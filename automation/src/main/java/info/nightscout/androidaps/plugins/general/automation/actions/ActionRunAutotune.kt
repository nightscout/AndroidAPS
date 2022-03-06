package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.Autotune
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import javax.inject.Inject

class ActionRunAutotune(injector: HasAndroidInjector) : Action(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var autotunePlugin: Autotune
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var sp: SP

    override fun friendlyName(): Int = R.string.autotune_run
    override fun shortDescription(): String = resourceHelper.gs(R.string.autotune_profile, profileFunction.getProfileName())
    @DrawableRes override fun icon(): Int = R.drawable.ic_actions_profileswitch

    override fun doAction(callback: Callback) {
        if(sp.getBoolean(R.string.key_autotune_auto, false)) {
            autotunePlugin.aapsAutotune()
            var message = R.string.autotune_run_with_autoswitch
            /* Todo, get end result and send message according to succeed or not (rxbus to update)
            if (!autotunePlugin.lastRunSuccess) {
                message = R.string.autotune_run_with_error
                aapsLogger.error(LTag.AUTOMATION, "Error during Autotune Run")
            }
             */
            callback.result(PumpEnactResult(injector).success(autotunePlugin.lastRunSuccess).comment(message))?.run()
            return
        } else {
            autotunePlugin.aapsAutotune()
            var message = R.string.autotune_run_without_autoswitch
            /*
            if (!autotunePlugin.lastRunSuccess) {
                message = R.string.autotune_run_with_error
                aapsLogger.error(LTag.AUTOMATION, "Error during Autotune Run")
            }
             */
            callback.result(PumpEnactResult(injector).success(autotunePlugin.lastRunSuccess).comment(message))?.run()
            return
        }
    }

    override fun hasDialog(): Boolean = false

    override fun isValid(): Boolean = profileFunction.getProfile() != null
}