package app.aaps.plugins.sync.nfcCommands

import androidx.annotation.StringRes
import app.aaps.plugins.sync.R
import app.aaps.core.ui.R as CoreUiR

/**
 * Defines elementary argument types for NFC Actions.
 * These types are used by the UI to dynamically build configuration forms.
 * Which field of [app.aaps.plugins.sync.nfcCommands.NfcParams] an argument fills is decided by the
 * `when` in `GenericNfcUiAction`, so an argument type carries no key of its own.
 */
enum class ArgType {
    NONE,
    DURATION,
    INSULIN,
    RATE,
    PERCENT,
    AMOUNT_GRAMS,
    MEAL_CHECK,
    PROFILE_NAME,
    SCENE_ID,
    GLUCOSE_TARGET,
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
 * Registry of all available NFC commands.
 *
 * The code-to-action mapping lives in [NfcActionFactory], not here, so this enum names no action class
 * and needs none of their dependencies.
 */
enum class NfcCommandCode(
    val category: NfcCategory
) {
    // Loop Management
    LOOP_STOP(NfcCategory.LOOP),
    LOOP_RESUME(NfcCategory.LOOP),
    LOOP_SUSPEND(NfcCategory.LOOP),
    LOOP_CLOSED(NfcCategory.LOOP),
    LOOP_LGS(NfcCategory.LOOP),
    
    // Pump Control
    PUMP_CONNECT(NfcCategory.PUMP),
    PUMP_DISCONNECT(NfcCategory.PUMP),
    
    // Basal Rate
    BASAL_STOP(NfcCategory.BASAL),
    BASAL_ABS(NfcCategory.BASAL),
    BASAL_PCT(NfcCategory.BASAL),
    
    // Treatments
    BOLUS(NfcCategory.TREATMENTS),
    CARBS(NfcCategory.TREATMENTS),
    BOLUS_WIZARD(NfcCategory.TREATMENTS),
    EXTENDED_STOP(NfcCategory.TREATMENTS),
    EXTENDED_SET(NfcCategory.TREATMENTS),
    
    // Profile
    PROFILE_SWITCH(NfcCategory.PROFILE),
    
    // Scenes
    RUN_SCENE(NfcCategory.SCENES),
    
    // Temporary Targets
    TARGET_MEAL(NfcCategory.TARGETS),
    TARGET_ACTIVITY(NfcCategory.TARGETS),
    TARGET_HYPO(NfcCategory.TARGETS),
    TARGET_MANUAL(NfcCategory.TARGETS),
    TARGET_STOP(NfcCategory.TARGETS),
    
    // Maintenance
    //AAPSCLIENT_RESTART(NfcCategory.SYSTEM),
    //RESTART(NfcCategory.SYSTEM);
}

/**
 * Logic to build the list of available categories and commands based on current system state.
 */
object NfcCategories {
    /**
     * Scans all [NfcCommandCode]s and returns only those supported by the current hardware/configuration,
     * grouped by their [NfcCategory].
     */
    fun build(actionFactory: NfcActionFactory): List<NfcUiCategory> {
        return NfcCommandCode.entries
            .map { it to actionFactory.create(it) }
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
