package app.aaps.ui.search

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAnnouncement
import app.aaps.core.ui.compose.icons.IcBgCheck
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCalculator
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcCgmInsert
import app.aaps.core.ui.compose.icons.IcClinicalNotes
import app.aaps.core.ui.compose.icons.IcHistory
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcPumpBattery
import app.aaps.core.ui.compose.icons.IcPumpCartridge
import app.aaps.core.ui.compose.icons.IcQuestion
import app.aaps.core.ui.compose.icons.IcQuickwizard
import app.aaps.core.ui.compose.icons.IcSetupWizard
import app.aaps.core.ui.compose.icons.IcStats
import app.aaps.core.ui.compose.icons.IcTtHigh
import app.aaps.core.ui.search.SearchableItem
import app.aaps.core.ui.search.SearchableProvider
import javax.inject.Inject
import javax.inject.Singleton
import app.aaps.core.objects.R as CoreObjectsR
import app.aaps.core.ui.R as CoreUiR

/**
 * Provides searchable items for dialogs and action screens.
 * These are navigable screens/dialogs accessible from search results.
 */
@Singleton
class DialogSearchables @Inject constructor() : SearchableProvider {

    override fun getSearchableItems(): List<SearchableItem> = buildList {
        // Drawer menu screens
        add(
            SearchableItem.Dialog(
                dialogKey = "treatments",
                dialogTitleResId = CoreUiR.string.treatments,
                dialogIcon = IcClinicalNotes,
                dialogSummaryResId = CoreUiR.string.treatments_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "stats",
                dialogTitleResId = app.aaps.ui.R.string.statistics,
                dialogIcon = IcStats,
                dialogSummaryResId = app.aaps.ui.R.string.statistics_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "stats_cycle_pattern",
                dialogTitleResId = app.aaps.ui.R.string.tdd_cycle_pattern,
                dialogIcon = IcStats,
                dialogSummaryResId = app.aaps.ui.R.string.tdd_cycle_pattern_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "profile_helper",
                dialogTitleResId = app.aaps.ui.R.string.nav_profile_helper,
                dialogIcon = IcProfile,
                dialogSummaryResId = app.aaps.ui.R.string.nav_profile_helper_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "history_browser",
                dialogTitleResId = CoreUiR.string.nav_history_browser,
                dialogIcon = IcHistory,
                dialogSummaryResId = CoreUiR.string.nav_history_browser_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "setup_wizard",
                dialogTitleResId = CoreUiR.string.nav_setupwizard,
                dialogIcon = IcSetupWizard,
                dialogSummaryResId = CoreUiR.string.nav_setupwizard_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "about",
                dialogTitleResId = CoreUiR.string.nav_about,
                dialogIcon = Icons.Default.Info,
                dialogSummaryResId = CoreUiR.string.nav_about_desc
            )
        )

        // Action screens (from ManageBottomSheet)
        add(
            SearchableItem.Dialog(
                dialogKey = "running_mode",
                dialogTitleResId = CoreUiR.string.running_mode,
                dialogIconResId = CoreObjectsR.drawable.ic_loop_closed
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "temp_target_management",
                dialogTitleResId = CoreUiR.string.temp_target_management,
                dialogIcon = IcTtHigh,
                dialogSummaryResId = CoreUiR.string.manage_temp_target_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "quick_wizard_management",
                dialogTitleResId = CoreUiR.string.quickwizard_managemnt,
                dialogIcon = IcQuickwizard,
                dialogSummaryResId = CoreUiR.string.manage_quickwizard_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "quick_launch_config",
                dialogTitleResId = app.aaps.ui.R.string.quick_launch_configure,
                dialogIcon = Icons.Default.Settings,
                dialogSummaryResId = app.aaps.ui.R.string.quick_launch_configure_desc
            )
        )

        // Dialogs (from TreatmentBottomSheet)
        add(
            SearchableItem.Dialog(
                dialogKey = "carbs_dialog",
                dialogTitleResId = CoreUiR.string.carbs,
                dialogIcon = IcCarbs,
                dialogSummaryResId = CoreUiR.string.treatment_carbs_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "insulin_dialog",
                dialogTitleResId = CoreUiR.string.overview_insulin_label,
                dialogIcon = IcBolus,
                dialogSummaryResId = CoreUiR.string.treatment_insulin_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "treatment_dialog",
                dialogTitleResId = CoreUiR.string.overview_treatment_label,
                dialogIcon = IcBolus,
                dialogSummaryResId = CoreUiR.string.treatment_desc
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "fill_dialog",
                dialogTitleResId = CoreUiR.string.prime_fill,
                dialogIcon = IcPumpCartridge
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "wizard_dialog",
                dialogTitleResId = CoreUiR.string.boluswizard,
                dialogIcon = IcCalculator
            )
        )

        // CareDialog events
        addCareDialogEvents()
    }

    private fun MutableList<SearchableItem>.addCareDialogEvents() {
        add(
            SearchableItem.Dialog(
                dialogKey = "care_${UiInteraction.EventType.BGCHECK.name.lowercase()}",
                dialogTitleResId = CoreUiR.string.careportal_bgcheck,
                dialogIcon = IcBgCheck
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "care_${UiInteraction.EventType.SENSOR_INSERT.name.lowercase()}",
                dialogTitleResId = CoreUiR.string.cgm_sensor_insert,
                dialogIcon = IcCgmInsert
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "care_${UiInteraction.EventType.BATTERY_CHANGE.name.lowercase()}",
                dialogTitleResId = CoreUiR.string.pump_battery_change,
                dialogIcon = IcPumpBattery
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "care_${UiInteraction.EventType.NOTE.name.lowercase()}",
                dialogTitleResId = CoreUiR.string.careportal_note,
                dialogIcon = IcNote
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "care_${UiInteraction.EventType.EXERCISE.name.lowercase()}",
                dialogTitleResId = CoreUiR.string.careportal_exercise,
                dialogIcon = IcActivity
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "care_${UiInteraction.EventType.QUESTION.name.lowercase()}",
                dialogTitleResId = CoreUiR.string.careportal_question,
                dialogIcon = IcQuestion
            )
        )
        add(
            SearchableItem.Dialog(
                dialogKey = "care_${UiInteraction.EventType.ANNOUNCEMENT.name.lowercase()}",
                dialogTitleResId = CoreUiR.string.careportal_announcement,
                dialogIcon = IcAnnouncement
            )
        )
    }
}
