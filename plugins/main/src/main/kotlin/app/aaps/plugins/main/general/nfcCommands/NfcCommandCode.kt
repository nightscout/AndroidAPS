package app.aaps.plugins.main.general.nfcCommands

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.ui.compose.icons.*
import app.aaps.core.ui.compose.navigation.ElementType
import app.aaps.plugins.main.R

enum class ArgType { NONE, SUSPEND, PUMP_DISCONNECT, BOLUS, BASAL_ABS, BASAL_PCT, EXTENDED, CARBS, PROFILE }

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
    val icon: ImageVector
) {
    LOOP_STOP(R.string.nfccommands_cmd_loop_stop, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopDisabled),
    LOOP_RESUME(R.string.nfccommands_cmd_loop_resume, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopClosed),
    LOOP_SUSPEND(R.string.nfccommands_cmd_loop_suspend, NfcCategory.LOOP, ElementType.LOOP, ArgType.SUSPEND, IcLoopPaused),
    LOOP_CLOSED(R.string.nfccommands_cmd_loop_closed, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopClosed),
    LOOP_LGS(R.string.nfccommands_cmd_loop_lgs, NfcCategory.LOOP, ElementType.LOOP, ArgType.NONE, IcLoopLgs),
    
    PUMP_CONNECT(R.string.nfccommands_cmd_pump_connect, NfcCategory.PUMP, ElementType.PUMP, ArgType.NONE, IcLoopReconnect),
    PUMP_DISCONNECT(R.string.nfccommands_cmd_pump_disconnect, NfcCategory.PUMP, ElementType.PUMP, ArgType.PUMP_DISCONNECT, IcLoopDisconnected),
    
    BASAL_STOP(R.string.nfccommands_cmd_basal_stop, NfcCategory.BASAL, ElementType.TEMP_BASAL, ArgType.NONE, IcTbrCancel),
    BASAL_ABS(R.string.nfccommands_cmd_basal_absolute, NfcCategory.BASAL, ElementType.TEMP_BASAL, ArgType.BASAL_ABS, IcTbrHigh),
    BASAL_PCT(R.string.nfccommands_cmd_basal_percent, NfcCategory.BASAL, ElementType.TEMP_BASAL, ArgType.BASAL_PCT, IcTbrLow),
    
    BOLUS(R.string.nfccommands_cmd_bolus, NfcCategory.BOLUS, ElementType.INSULIN, ArgType.BOLUS, IcBolus),
    EXTENDED_STOP(R.string.nfccommands_cmd_extended_stop, NfcCategory.BOLUS, ElementType.EXTENDED_BOLUS, ArgType.NONE, IcCancelExtendedBolus),
    EXTENDED_SET(R.string.nfccommands_cmd_extended_bolus, NfcCategory.BOLUS, ElementType.EXTENDED_BOLUS, ArgType.EXTENDED, IcExtendedBolus),
    CARBS(R.string.nfccommands_cmd_carbs, NfcCategory.BOLUS, ElementType.CARBS, ArgType.CARBS, IcCarbs),
    
    PROFILE_SWITCH(R.string.nfccommands_cmd_profile_switch, NfcCategory.PROFILE, ElementType.PROFILE_MANAGEMENT, ArgType.PROFILE, IcProfile),
    
    TARGET_MEAL(R.string.nfccommands_cmd_target_meal, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtEatingSoon),
    TARGET_ACTIVITY(R.string.nfccommands_cmd_target_activity, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtActivity),
    TARGET_HYPO(R.string.nfccommands_cmd_target_hypo, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtHypo),
    TARGET_STOP(R.string.nfccommands_cmd_target_stop, NfcCategory.TARGETS, ElementType.TEMP_TARGET_MANAGEMENT, ArgType.NONE, IcTtCancel),
    
    AAPSCLIENT_RESTART(R.string.nfccommands_cmd_aapsclient_restart, NfcCategory.SYSTEM, ElementType.SETTINGS, ArgType.NONE, IcPluginNsClient),
    RESTART(R.string.nfccommands_cmd_restart_aaps, NfcCategory.SYSTEM, ElementType.AAPS, ArgType.NONE, IcAaps)
}
