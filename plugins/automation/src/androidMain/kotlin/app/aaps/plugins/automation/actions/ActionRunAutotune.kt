package app.aaps.plugins.automation.actions

import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.plugins.automation.AutomationStrings
import app.aaps.core.interfaces.autotune.Autotune
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.pump.comment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.icons.IcPluginAutotune
import app.aaps.core.ui.elements.WeekDay
import app.aaps.core.utils.lenientBoolean
import app.aaps.core.utils.lenientInt
import app.aaps.core.utils.lenientString
import app.aaps.plugins.automation.R
import app.aaps.plugins.automation.elements.InputDuration
import app.aaps.plugins.automation.elements.InputProfileName
import app.aaps.plugins.automation.elements.InputWeekDay
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ActionRunAutotune(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    pumpEnactResultProvider: Provider<PumpEnactResult>,
    private val resourceHelper: ResourceHelper,
    private val autotunePlugin: Autotune,
    private val profileFunction: ProfileFunction,
    private val activePlugin: ActivePlugin,
    private val preferences: Preferences
) : Action(aapsLogger, rh, pumpEnactResultProvider) {


    private var defaultValue = 0
    private var inputProfileName = InputProfileName("")
    private var daysBack = InputDuration(0, InputDuration.TimeUnit.DAYS)
    private val days = InputWeekDay().also { it.setAll(true) }

    override fun friendlyName(): TextRef = AutomationStrings.autotune_run
    override fun shortDescription(): String = resourceHelper.gs(AutomationStrings.autotune_profile_name, inputProfileName.value)
    override fun composeIcon() = IcPluginAutotune
    override fun elementType() = ElementType.PROFILE_MANAGEMENT

    override suspend fun doAction(): PumpEnactResult {
        val autoSwitch = preferences.get(BooleanKey.AutotuneAutoSwitchProfile)
        val profileName = if (inputProfileName.value == rh.gs(CoreUiStrings.active)) "" else inputProfileName.value
        var message = if (autoSwitch) R.string.autotune_run_with_autoswitch else R.string.autotune_run_without_autoswitch
        return if (!autotunePlugin.calculationRunning) {
            autotunePlugin.atLog("[Automation] Run Autotune $profileName, ${daysBack.value} days, Autoswitch $autoSwitch")
            // aapsAutotune is suspend; runs heavy work but uses suspend I/O internally — keep
            // the explicit IO dispatcher to push the CPU+I/O loop off the caller's dispatcher.
            withContext(Dispatchers.IO) {
                autotunePlugin.aapsAutotune(daysBack.value, autoSwitch, profileName, days.weekdays)
            }
            if (!autotunePlugin.lastRunSuccess) {
                message = R.string.autotune_run_with_error
                aapsLogger.error(LTag.AUTOMATION, "Error during Autotune Run")
            }
            pumpEnactResultProvider().success(autotunePlugin.lastRunSuccess).comment(message)
        } else {
            message = R.string.autotune_run_cancelled
            aapsLogger.debug(LTag.AUTOMATION, "Autotune run detected, Autotune Run Cancelled")
            pumpEnactResultProvider().success(false).comment(message)
        }
    }

    override fun hasDialog(): Boolean = true

    override fun toJSON(): String {
        val data = buildJsonObject {
            put("profileToTune", inputProfileName.value)
            put("tunedays", daysBack.value)
            for (i in days.weekdays.indices) {
                put(WeekDay.DayOfWeek.entries[i].name, days.weekdays[i])
            }
        }
        return buildJsonObject {
            put("type", this@ActionRunAutotune.javaClass.simpleName)
            put("data", data)
        }.toString()
    }

    override fun fromJSON(data: String): Action {
        val o = jsonOf(data)
        for (i in days.weekdays.indices)
            days.weekdays[i] = o.lenientBoolean(WeekDay.DayOfWeek.entries[i].name, true)
        inputProfileName.value = o.lenientString("profileToTune", "")
        defaultValue = o.lenientInt("tunedays")
        if (defaultValue == 0)
            defaultValue = preferences.get(IntKey.AutotuneDefaultTuneDays)
        daysBack.value = defaultValue
        return this
    }

    override fun isValid(): Boolean = runBlocking { profileFunction.getProfile() } != null && activePlugin.getSpecificPluginsListByInterface(Autotune::class).first().isEnabled()
}