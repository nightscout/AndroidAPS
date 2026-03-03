package app.aaps.ui.compose.quickLaunch

/**
 * Represents an action that can be placed on the quick-action toolbar.
 *
 * Static actions are singletons (dialog shortcuts).
 * Dynamic actions carry a runtime identifier (GUID/name) and are validated on load.
 */
sealed class QuickLaunchAction {

    /** Type discriminator used for JSON serialization */
    abstract val typeId: String

    /** Runtime identifier for dynamic actions; null for static */
    open val dynamicId: String? get() = null

    /** Category for grouping in the configuration screen */
    abstract val category: Category

    // ── Static actions (dialog shortcuts) ──

    data object Insulin : QuickLaunchAction() {

        override val typeId = "insulin"
        override val category = Category.TREATMENT
    }

    data object Carbs : QuickLaunchAction() {

        override val typeId = "carbs"
        override val category = Category.TREATMENT
    }

    data object Wizard : QuickLaunchAction() {

        override val typeId = "wizard"
        override val category = Category.TREATMENT
    }

    data object Treatment : QuickLaunchAction() {

        override val typeId = "treatment"
        override val category = Category.TREATMENT
    }

    data object Cgm : QuickLaunchAction() {

        override val typeId = "cgm"
        override val category = Category.TREATMENT
    }

    data object Calibration : QuickLaunchAction() {

        override val typeId = "calibration"
        override val category = Category.TREATMENT
    }

    data object ProfileSwitch : QuickLaunchAction() {

        override val typeId = "profile_switch"
        override val category = Category.PROFILE
    }

    data object BgCheck : QuickLaunchAction() {

        override val typeId = "bg_check"
        override val category = Category.CARE
    }

    data object Note : QuickLaunchAction() {

        override val typeId = "note"
        override val category = Category.CARE
    }

    data object Exercise : QuickLaunchAction() {

        override val typeId = "exercise"
        override val category = Category.CARE
    }

    data object Question : QuickLaunchAction() {

        override val typeId = "question"
        override val category = Category.CARE
    }

    data object Announcement : QuickLaunchAction() {

        override val typeId = "announcement"
        override val category = Category.CARE
    }

    data object SensorInsert : QuickLaunchAction() {

        override val typeId = "sensor_insert"
        override val category = Category.CARE
    }

    data object BatteryChange : QuickLaunchAction() {

        override val typeId = "battery_change"
        override val category = Category.CARE
    }

    data object CannulaChange : QuickLaunchAction() {

        override val typeId = "cannula_change"
        override val category = Category.CARE
    }

    data object Fill : QuickLaunchAction() {

        override val typeId = "fill"
        override val category = Category.CARE
    }

    data object SiteRotation : QuickLaunchAction() {

        override val typeId = "site_rotation"
        override val category = Category.CARE
    }

    /** Always-present config button — cannot be removed, always last */
    data object QuickLaunchConfig : QuickLaunchAction() {

        override val typeId = "quick_launch_config"
        override val category = Category.SYSTEM
    }

    // ── Dynamic actions (carry runtime identifier) ──

    data class QuickWizardAction(val guid: String) : QuickLaunchAction() {

        override val typeId = "quick_wizard"
        override val dynamicId = guid
        override val category = Category.QUICK_WIZARD
    }

    data class AutomationAction(val automationId: String) : QuickLaunchAction() {

        override val typeId = "automation"
        override val dynamicId = automationId
        override val category = Category.AUTOMATION
    }

    data class TempTargetPreset(val presetId: String) : QuickLaunchAction() {

        override val typeId = "tt_preset"
        override val dynamicId = presetId
        override val category = Category.TEMP_TARGET
    }

    data class ProfileAction(
        val profileName: String,
        val percentage: Int = 100,
        val durationMinutes: Int = 0 // 0 = permanent
    ) : QuickLaunchAction() {

        override val typeId = "profile"
        override val dynamicId = "${profileName}_${percentage}_${durationMinutes}"
        override val category = Category.PROFILE
    }

    data class PluginAction(val className: String) : QuickLaunchAction() {

        override val typeId = "plugin"
        override val dynamicId = className
        override val category = Category.PLUGIN
    }

    /** Categories for grouping actions in the configuration screen */
    enum class Category {

        TREATMENT,
        CARE,
        PROFILE,
        TEMP_TARGET,
        QUICK_WIZARD,
        AUTOMATION,
        PLUGIN,
        SYSTEM
    }

    companion object {

        /** All static actions available for the configuration screen (excluding ToolbarConfig) */
        val staticActions: List<QuickLaunchAction> = listOf(
            Insulin, Carbs, Wizard, Treatment, Cgm, Calibration,
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
