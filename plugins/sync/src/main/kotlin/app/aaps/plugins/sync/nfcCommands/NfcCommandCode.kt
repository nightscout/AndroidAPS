package app.aaps.plugins.sync.nfcCommands

import androidx.annotation.StringRes
import app.aaps.plugins.sync.nfcCommands.actions.*
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR

/**
 * Defines elementary argument types for NFC Actions.
 * These types are used by the UI to dynamically build configuration forms.
 * [jsonKey] indicates which field in the parameters JSON this argument corresponds to.
 */
enum class ArgType(val jsonKey: String? = null) {
    NONE,
    DURATION(NfcJsonKeys.DURATION),
    INSULIN(NfcJsonKeys.AMOUNT),
    RATE(NfcJsonKeys.RATE),
    PERCENT(NfcJsonKeys.PERCENT),
    AMOUNT_GRAMS(NfcJsonKeys.AMOUNT),
    MEAL_CHECK(NfcJsonKeys.IS_MEAL),
    PROFILE_NAME(NfcJsonKeys.PROFILE_NAME),
    SCENE_ID(NfcJsonKeys.SCENE_ID),
    GLUCOSE_TARGET(NfcJsonKeys.GLUCOSE),
    BOLUS_WIZARD_OPTIONS // Special composite type for calculator toggles
}

/**
 * Categories for grouping NFC commands in the picker UI.
 */
enum class NfcCategory(@StringRes val labelResId: Int) {
    LOOP(CoreUiR.string.loop),
    PUMP(CoreUiR.string.pump),
    BASAL(CoreUiR.string.basal),
    TREATMENTS(CoreUiR.string.treatments),
    PROFILE(CoreUiR.string.profile),
    SCENES(CoreUiR.string.scenes),
    TARGETS(R.string.nfccommands_cat_targets),
    SYSTEM(R.string.nfccommands_cat_system)
}

/**
 * Data structure representing a group of commands in the NFC action picker.
 */
data class NfcUiCategory(
    val labelResId: Int,
    val commands: List<NfcCommandCode>,
)

/**
 * Registry of all available NFC commands and their factory methods.
 * Each entry maps a command code to its corresponding [NfcAction] implementation and [NfcCategory].
 */
enum class NfcCommandCode(
    val category: NfcCategory,
    val createAction: (NfcCommandsPlugin) -> NfcAction
) {
    // Loop Management
    LOOP_STOP(NfcCategory.LOOP, ::LoopStopAction),
    LOOP_RESUME(NfcCategory.LOOP, ::LoopResumeAction),
    LOOP_SUSPEND(NfcCategory.LOOP, ::LoopSuspendAction),
    LOOP_CLOSED(NfcCategory.LOOP, ::LoopClosedAction),
    LOOP_LGS(NfcCategory.LOOP, ::LoopLgsAction),
    
    // Pump Control
    PUMP_CONNECT(NfcCategory.PUMP, ::PumpConnectAction),
    PUMP_DISCONNECT(NfcCategory.PUMP, ::PumpDisconnectAction),
    
    // Basal Rate
    BASAL_STOP(NfcCategory.BASAL, ::BasalCancelAction),
    BASAL_ABS(NfcCategory.BASAL, ::TempBasalAbsoluteAction),
    BASAL_PCT(NfcCategory.BASAL, ::TempBasalPercentAction),
    
    // Treatments
    BOLUS(NfcCategory.TREATMENTS, ::BolusAction),
    CARBS(NfcCategory.TREATMENTS, ::CarbsAction),
    BOLUS_WIZARD(NfcCategory.TREATMENTS, ::BolusWizardAction),
    EXTENDED_STOP(NfcCategory.TREATMENTS, ::ExtendedCancelAction),
    EXTENDED_SET(NfcCategory.TREATMENTS, ::ExtendedSetAction),
    
    // Profile
    PROFILE_SWITCH(NfcCategory.PROFILE, ::ProfileSwitchAction),
    
    // Scenes
    RUN_SCENE(NfcCategory.SCENES, ::RunSceneAction),
    
    // Temporary Targets
    TARGET_MEAL(NfcCategory.TARGETS, ::TempTargetMealAction),
    TARGET_ACTIVITY(NfcCategory.TARGETS, ::TempTargetActivityAction),
    TARGET_HYPO(NfcCategory.TARGETS, ::TempTargetHypoAction),
    TARGET_MANUAL(NfcCategory.TARGETS, ::TempTargetManualAction),
    TARGET_STOP(NfcCategory.TARGETS, ::TempTargetCancelAction),
    
    // Maintenance
    //AAPSCLIENT_RESTART(NfcCategory.SYSTEM, ::AapsClientRestartAction),
    //RESTART(NfcCategory.SYSTEM, ::RestartAction);
}

/**
 * Logic to build the list of available categories and commands based on current system state.
 */
object NfcCategories {
    /**
     * Scans all [NfcCommandCode]s and returns only those supported by the current hardware/configuration,
     * grouped by their [NfcCategory].
     */
    fun build(plugin: NfcCommandsPlugin): List<NfcUiCategory> {
        return NfcCommandCode.entries
            .map { it to it.createAction(plugin) }
            .filter { it.second.isSupported() }
            .groupBy { it.first.category }
            .map { (cat, pairs) ->
                NfcUiCategory(
                    labelResId = cat.labelResId,
                    commands = pairs.map { it.first }
                )
            }
    }
}
