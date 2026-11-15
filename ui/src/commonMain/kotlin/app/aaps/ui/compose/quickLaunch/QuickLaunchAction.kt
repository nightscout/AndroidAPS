package app.aaps.ui.compose.quickLaunch

import app.aaps.core.interfaces.navigation.ElementType

/**
 * Represents an action that can be placed on the quick-action toolbar.
 *
 * Static actions wrap an [ElementType] — typeId is derived automatically.
 * Dynamic actions carry a runtime identifier (GUID/name) and are validated on load.
 */
sealed class QuickLaunchAction {

    /** Type discriminator used for JSON serialization */
    abstract val typeId: String

    /** Runtime identifier for dynamic actions; null for static */
    open val dynamicId: String? get() = null

    /** Visual identity — icon, color, label, description. Null for non-element actions. */
    open val elementType: ElementType? get() = null

    // ── Static actions (dialog shortcuts) ──

    data class StaticAction(override val elementType: ElementType) : QuickLaunchAction() {

        override val typeId: String = elementType.name.lowercase()
    }

    // ── Dynamic actions (carry runtime identifier) ──

    data class QuickWizardAction(val guid: String) : QuickLaunchAction() {

        override val typeId = "quick_wizard"
        override val dynamicId = guid
        override val elementType = ElementType.QUICK_WIZARD
    }

    data class AutomationAction(val automationId: String) : QuickLaunchAction() {

        override val typeId = "automation"
        override val dynamicId = automationId
        override val elementType = ElementType.AUTOMATION
    }

    data class TempTargetPreset(val presetId: String) : QuickLaunchAction() {

        override val typeId = "tt_preset"
        override val dynamicId = presetId
        override val elementType = ElementType.TEMP_TARGET_MANAGEMENT
    }

    data class ProfileAction(
        val profileName: String,
        val percentage: Int = 100,
        val durationMinutes: Int = 0 // 0 = permanent
    ) : QuickLaunchAction() {

        override val typeId = "profile"
        override val dynamicId = "${profileName}_${percentage}_${durationMinutes}"
        override val elementType = ElementType.PROFILE_MANAGEMENT
    }

    data class SceneAction(val sceneId: String) : QuickLaunchAction() {

        override val typeId = "scene"
        override val dynamicId = sceneId
        override val elementType = ElementType.SCENE
    }

    data class PluginAction(val className: String) : QuickLaunchAction() {

        override val typeId = "plugin"
        override val dynamicId = className
        // No elementType — icon/color resolved at runtime via ViewModel
    }

    companion object {

        // Source-compatible aliases
        val Insulin = StaticAction(ElementType.INSULIN)
        val Carbs = StaticAction(ElementType.CARBS)
        val Wizard = StaticAction(ElementType.BOLUS_WIZARD)
        val Treatment = StaticAction(ElementType.TREATMENT)
        val Cgm = StaticAction(ElementType.CGM_XDRIP)
        val Calibration = StaticAction(ElementType.CALIBRATION)
        val InsulinManagement = StaticAction(ElementType.INSULIN_MANAGEMENT)
        val ProfileSwitch = StaticAction(ElementType.PROFILE_MANAGEMENT)
        val TempTargetManagement = StaticAction(ElementType.TEMP_TARGET_MANAGEMENT)
        val QuickWizardManagement = StaticAction(ElementType.QUICK_WIZARD_MANAGEMENT)
        val SceneManagement = StaticAction(ElementType.SCENE_MANAGEMENT)
        val AutomationManagement = StaticAction(ElementType.AUTOMATION_MANAGEMENT)
        val FoodManagement = StaticAction(ElementType.FOOD_MANAGEMENT)
        val RunningMode = StaticAction(ElementType.RUNNING_MODE)
        val AuthorizedClients = StaticAction(ElementType.AUTHORIZED_CLIENTS)
        val BgCheck = StaticAction(ElementType.BG_CHECK)
        val Note = StaticAction(ElementType.NOTE)
        val Exercise = StaticAction(ElementType.EXERCISE)
        val Question = StaticAction(ElementType.QUESTION)
        val Announcement = StaticAction(ElementType.ANNOUNCEMENT)
        val SensorInsert = StaticAction(ElementType.SENSOR_INSERT)
        val BatteryChange = StaticAction(ElementType.BATTERY_CHANGE)
        val CannulaChange = StaticAction(ElementType.CANNULA_CHANGE)
        val Fill = StaticAction(ElementType.FILL)
        val SiteRotation = StaticAction(ElementType.SITE_ROTATION)
        val QuickLaunchConfig = StaticAction(ElementType.QUICK_LAUNCH_CONFIG)

        /**
         * All static actions available for the configuration screen (excluding QuickLaunchConfig).
         *
         * The management block follows the order of the Manage sheet, so the two lists read the same
         * way, with `RUNNING_MODE` after it - that one is a management screen too, but the sheet does
         * not list it because the navigation bar already leads there. Two of the sheet's entries are
         * deliberately absent: `SCENE`, which starts a scene
         * rather than opening a screen and already has one [SceneAction] per scene, and
         * `PAIR_WITH_MASTER`, which is a one time setup step and not something to keep on a toolbar.
         */
        val staticActions: List<QuickLaunchAction> = listOf(
            Insulin, Carbs, Wizard, Treatment, Cgm, Calibration,
            ProfileSwitch, InsulinManagement, TempTargetManagement, QuickWizardManagement,
            SceneManagement, AutomationManagement, FoodManagement, RunningMode,
            AuthorizedClients,
            BgCheck, Note, Exercise, Question, Announcement,
            SensorInsert, BatteryChange, CannulaChange, Fill, SiteRotation
        )

        /** Default toolbar configuration */
        val default: List<QuickLaunchAction> = listOf(Wizard, QuickLaunchConfig)

        /** Lookup static action by typeId */
        fun fromTypeId(typeId: String): QuickLaunchAction? = staticActions.find { it.typeId == typeId }
            ?: if (typeId == QuickLaunchConfig.typeId) QuickLaunchConfig else null
    }
}
