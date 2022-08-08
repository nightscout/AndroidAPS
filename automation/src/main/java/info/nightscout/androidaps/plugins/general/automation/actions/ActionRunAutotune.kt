package info.nightscout.androidaps.plugins.general.automation.actions

import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.automation.R
import info.nightscout.androidaps.data.PumpEnactResult
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.Autotune
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.ResourceHelper
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.general.automation.elements.InputDuration
import info.nightscout.androidaps.plugins.general.automation.elements.InputProfileName
import info.nightscout.androidaps.plugins.general.automation.elements.LabelWithElement
import info.nightscout.androidaps.plugins.general.automation.elements.LayoutBuilder
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.JsonHelper
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import org.json.JSONObject
import javax.inject.Inject

class ActionRunAutotune(injector: HasAndroidInjector) : Action(injector) {

    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var autotunePlugin: Autotune
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var sp: SP

    var defaultValue = 0
    private var inputProfileName = InputProfileName(rh, activePlugin, "", true)
    private var daysBack = InputDuration(0, InputDuration.TimeUnit.DAYS)

    override fun friendlyName(): Int = R.string.autotune_run
    override fun shortDescription(): String = resourceHelper.gs(R.string.autotune_profile_name, inputProfileName.value)
    @DrawableRes override fun icon(): Int = R.drawable.ic_actions_profileswitch

    override fun doAction(callback: Callback) {
        val autoSwitch = sp.getBoolean(R.string.key_autotune_auto, false)
        val profileName = if (inputProfileName.value == rh.gs(R.string.active)) "" else inputProfileName.value
        var message = if (autoSwitch) R.string.autotune_run_with_autoswitch else R.string.autotune_run_without_autoswitch
        Thread {
            if (!autotunePlugin.calculationRunning) {
                autotunePlugin.atLog("[Automation] Run Autotune $profileName, ${daysBack.value} days, Autoswitch $autoSwitch")
                autotunePlugin.aapsAutotune(daysBack.value, autoSwitch, profileName)
                if (!autotunePlugin.lastRunSuccess) {
                    message = R.string.autotune_run_with_error
                    aapsLogger.error(LTag.AUTOMATION, "Error during Autotune Run")
                }
                callback.result(PumpEnactResult(injector).success(autotunePlugin.lastRunSuccess).comment(message)).run()
            } else {
                message = R.string.autotune_run_cancelled
                aapsLogger.debug(LTag.AUTOMATION, "Autotune run detected, Autotune Run Cancelled")
                callback.result(PumpEnactResult(injector).success(false).comment(message)).run()
            }
        }.start()
        return
    }

    override fun generateDialog(root: LinearLayout) {
        if (defaultValue == 0)
            defaultValue = sp.getInt(R.string.key_autotune_default_tune_days, 5)
        daysBack.value = defaultValue
        LayoutBuilder()
            .add(LabelWithElement(rh, rh.gs(R.string.autotune_select_profile), "", inputProfileName))
            .add(LabelWithElement(rh, rh.gs(R.string.autotune_tune_days), "", daysBack))
            .build(root)
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = JSONObject()
            .put("profileToTune", inputProfileName.value)
            .put("tunedays", daysBack.value)
        return JSONObject()
            .put("type", this.javaClass.name)
            .put("data", data)
            .toString()
    }

    override fun fromJSON(data: String): Action {
        val o = JSONObject(data)
        inputProfileName.value = JsonHelper.safeGetString(o, "profileToTune", "")
        defaultValue = JsonHelper.safeGetInt(o, "tunedays")
        if (defaultValue == 0)
            defaultValue = sp.getInt(R.string.key_autotune_default_tune_days, 5)
        daysBack.value = defaultValue
        return this
    }

    override fun isValid(): Boolean = profileFunction.getProfile() != null && activePlugin.getSpecificPluginsListByInterface(Autotune::class.java).first().isEnabled()
}