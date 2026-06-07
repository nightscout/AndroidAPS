package app.aaps.plugins.main.general.nfcCommands

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.icons.*
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.core.ui.compose.navigation.color
import app.aaps.plugins.main.R
import org.json.JSONObject
import app.aaps.core.keys.R as KeysR
import app.aaps.core.ui.R as CoreUiR

enum class ArgType {
    NONE,
    SUSPEND,
    PUMP_DISCONNECT,
    BOLUS,
    BASAL_ABS,
    BASAL_PCT,
    EXTENDED,
    CARBS,
    PROFILE;

    @Composable
    fun formatParams(params: JSONObject, plugin: NfcCommandsPlugin): String {
        return when (this) {
            BOLUS -> {
                val amount = params.optDouble("amount", 0.0)
                val isMeal = params.optBoolean("isMeal", false)
                val unit = stringResource(CoreUiR.string.insulin_unit_shortname)
                val mealText = if (isMeal) " (${stringResource(R.string.nfccommands_meal_bolus)})" else ""
                "${plugin.decimalFormatter.to2Decimal(amount)} $unit$mealText"
            }
            CARBS -> {
                val amount = params.optInt("amount", 0)
                val unit = stringResource(CoreUiR.string.shortgramm)
                "$amount $unit"
            }
            SUSPEND, PUMP_DISCONNECT -> {
                val duration = params.optInt("duration", 0)
                val unit = stringResource(KeysR.string.units_min)
                "$duration $unit"
            }
            BASAL_ABS -> {
                val rate = params.optDouble("rate", 0.0)
                val duration = params.optInt("duration", 0)
                val unitRate = stringResource(CoreUiR.string.profile_ins_units_per_hour)
                val unitDur = stringResource(KeysR.string.units_min)
                "${plugin.decimalFormatter.to2Decimal(rate)} $unitRate $duration $unitDur"
            }
            BASAL_PCT -> {
                val percent = params.optInt("percent", 100)
                val duration = params.optInt("duration", 0)
                val unitDur = stringResource(KeysR.string.units_min)
                "$percent% $duration $unitDur"
            }
            EXTENDED -> {
                val amount = params.optDouble("amount", 0.0)
                val duration = params.optInt("duration", 0)
                val unitAmt = stringResource(CoreUiR.string.insulin_unit_shortname)
                val unitDur = stringResource(KeysR.string.units_min)
                "${plugin.decimalFormatter.to2Decimal(amount)} $unitAmt $duration $unitDur"
            }
            PROFILE -> {
                val name = params.optString("profileName", "")
                val pct = params.optInt("percentage", 100)
                if (name.isNotEmpty()) "$name $pct%"
                else {
                    val fallback = plugin.profileRepository.profile.value?.getDefaultProfileName() ?: ""
                    "$fallback $pct%"
                }
            }
            NONE -> ""
        }
    }

    suspend fun getDefaultParams(plugin: NfcCommandsPlugin): JSONObject {
        return when (this) {
            BOLUS -> JSONObject().put("amount", 0.0).put("isMeal", false)
            CARBS -> JSONObject().put("amount", 0)
            SUSPEND -> JSONObject().put("duration", 60)
            PUMP_DISCONNECT -> JSONObject().put("duration", 30)
            BASAL_ABS -> JSONObject().put("rate", 0.0).put("duration", plugin.pumpBasalDurationStep())
            BASAL_PCT -> JSONObject().put("percent", 100).put("duration", plugin.pumpBasalDurationStep())
            EXTENDED -> JSONObject().put("amount", 0.0).put("duration", 30)
            PROFILE -> {
                val profileName = plugin.profileFunction.getOriginalProfileName()
                JSONObject().put("profileName", profileName).put("percentage", 100)
            }
            NONE -> JSONObject()
        }
    }
}

enum class NfcCategory(@StringRes val labelResId: Int, @StringRes val docAnchorResId: Int) {
    LOOP(R.string.nfccommands_cat_loop, R.string.nfccommands_doc_anchor_loop),
    PUMP(R.string.nfccommands_cat_pump, R.string.nfccommands_doc_anchor_pump),
    BASAL(R.string.nfccommands_cat_basal, R.string.nfccommands_doc_anchor_basal),
    BOLUS(R.string.nfccommands_cat_bolus, R.string.nfccommands_doc_anchor_bolus),
    PROFILE(R.string.nfccommands_cat_profile, R.string.nfccommands_doc_anchor_profile),
    TARGETS(R.string.nfccommands_cat_targets, R.string.nfccommands_doc_anchor_targets),
    SYSTEM(R.string.nfccommands_cat_system, R.string.nfccommands_doc_anchor_system)
}

enum class NfcCommandCode(
    @StringRes val labelResId: Int,
    val category: NfcCategory,
    val elementType: ElementType,
    val argType: ArgType,
    val icon: ImageVector,
    private val customIconColor: (@Composable () -> Color)? = null
) {
    LOOP_STOP(R.string.nfccommands_cmd_loop_stop, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopDisabled, { AapsTheme.elementColors.loopDisabled }),
    LOOP_RESUME(R.string.nfccommands_cmd_loop_resume, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopClosed, { AapsTheme.elementColors.loopClosed }),
    LOOP_SUSPEND(R.string.nfccommands_cmd_loop_suspend, NfcCategory.LOOP, ElementType.LOOP, ArgType.SUSPEND, IcLoopPaused, { AapsTheme.elementColors.loopSuspended }),
    LOOP_CLOSED(R.string.nfccommands_cmd_loop_closed, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopClosed, { AapsTheme.elementColors.loopClosed }),
    LOOP_LGS(R.string.nfccommands_cmd_loop_lgs, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopLgs, { AapsTheme.elementColors.loopLgs }),
    
    PUMP_CONNECT(R.string.nfccommands_cmd_pump_connect, NfcCategory.PUMP, ElementType.PUMP, ArgType.NONE, IcLoopReconnect),
    PUMP_DISCONNECT(R.string.nfccommands_cmd_pump_disconnect, NfcCategory.PUMP, ElementType.PUMP, ArgType.PUMP_DISCONNECT, IcLoopDisconnected, { AapsTheme.elementColors.loopDisconnected }),
    
    BASAL_STOP(R.string.nfccommands_cmd_basal_stop, NfcCategory.BASAL, ElementType.TEMP_BASAL, ArgType.NONE, IcTbrCancel),
    BASAL_ABS(R.string.nfccommands_cmd_basal_absolute, NfcCategory.BASAL, ElementType.TEMP_BASAL, ArgType.BASAL_ABS, IcTbrHigh),
    BASAL_PCT(R.string.nfccommands_cmd_basal_percent, NfcCategory.BASAL, ElementType.TEMP_BASAL, ArgType.BASAL_PCT, IcTbrLow),
    
    BOLUS(R.string.nfccommands_cmd_bolus, NfcCategory.BOLUS, ElementType.INSULIN, ArgType.BOLUS, IcBolus),
    EXTENDED_STOP(R.string.nfccommands_cmd_extended_stop, NfcCategory.BOLUS, ElementType.EXTENDED_BOLUS, ArgType.NONE, IcCancelExtendedBolus),
    EXTENDED_SET(R.string.nfccommands_cmd_extended_bolus, NfcCategory.BOLUS, ElementType.EXTENDED_BOLUS, ArgType.EXTENDED, IcExtendedBolus),
    CARBS(R.string.nfccommands_cmd_carbs, NfcCategory.BOLUS, ElementType.CARBS, ArgType.CARBS, IcCarbs),
    
    PROFILE_SWITCH(R.string.nfccommands_cmd_profile_switch, NfcCategory.PROFILE, ElementType.PROFILE_MANAGEMENT, ArgType.PROFILE, IcProfile, { if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) Color.Black else Color.White }),
    
    TARGET_MEAL(R.string.nfccommands_cmd_target_meal, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtEatingSoon, { AapsTheme.elementColors.tempTarget }),
    TARGET_ACTIVITY(R.string.nfccommands_cmd_target_activity, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtActivity, { AapsTheme.elementColors.exercise }),
    TARGET_HYPO(R.string.nfccommands_cmd_target_hypo, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtHypo, { AapsTheme.elementColors.loopDisabled }),
    TARGET_STOP(R.string.nfccommands_cmd_target_stop, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtCancel, { AapsTheme.elementColors.loopDisabled }),
    
    AAPSCLIENT_RESTART(R.string.nfccommands_cmd_aapsclient_restart, NfcCategory.SYSTEM, ElementType.SETTINGS, ArgType.NONE, IcPluginNsClient),
    RESTART(R.string.nfccommands_cmd_restart_aaps, NfcCategory.SYSTEM, ElementType.AAPS, ArgType.NONE, IcAaps);

    @Composable
    fun getIconColor(): Color = customIconColor?.invoke() ?: elementType.color()

    @Composable
    fun formatParams(params: JSONObject, plugin: NfcCommandsPlugin): String = argType.formatParams(params, plugin)
}
