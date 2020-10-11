package info.nightscout.androidaps.plugins.general.automation.actions

import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.general.autotune.AutotunePlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

class ActionRunAutotune(injector: HasAndroidInjector) : Action(injector) {
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var autotunePlugin: AutotunePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var sp: SP

    override fun friendlyName(): Int = R.string.autotune_run
    override fun shortDescription(): String = resourceHelper.gs(R.string.autotune_profile, profileFunction.getProfileName())
    @DrawableRes override fun icon(): Int = R.drawable.ic_actions_profileswitch

    override fun doAction(callback: Callback) {
        if (profileFunction.getProfile() == null) {
            aapsLogger.error(LTag.AUTOMATION, "ProfileFunctions not initialized")
            callback.result(PumpEnactResult(injector).success(false).comment(R.string.noprofile))?.run()
            return
        }
        if(sp.getBoolean(R.string.key_autotune_auto, false)) {
            autotunePlugin.aapsAutotune()
            var message = R.string.autotune_run_with_autoswitch
            if (!AutotunePlugin.lastRunSuccess) {
                message = R.string.autotune_run_with_error
                aapsLogger.error(LTag.AUTOMATION, "Error during Autotune Run")
            }
            callback.result(PumpEnactResult(injector).success(AutotunePlugin.lastRunSuccess).comment(message))?.run()
            return
        } else {
            autotunePlugin.aapsAutotune()
            var message = R.string.autotune_run_without_autoswitch
            if (!AutotunePlugin.lastRunSuccess) {
                message = R.string.autotune_run_with_error
                aapsLogger.error(LTag.AUTOMATION, "Error during Autotune Run")
            }
            callback.result(PumpEnactResult(injector).success(AutotunePlugin.lastRunSuccess).comment(message))?.run()
            return
        }
    }

    override fun hasDialog(): Boolean = false

}