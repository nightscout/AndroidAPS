package app.aaps.ui.compose.scenes

import app.aaps.core.data.model.RM
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.ui.R

/**
 * Pre-defined scene templates for quick creation.
 * Default values are derived from published clinical guidelines (see FEATURE_BUNDLES.md)
 * and serve as starting points — the wizard lets users adjust everything.
 */
enum class SceneTemplate(
    val nameResId: Int,
    val descResId: Int,
    val infoResId: Int,
    val icon: String,
    val defaultDurationMinutes: Int,
    val defaultActions: List<SceneAction>
) {

    EXERCISE(
        nameResId = R.string.scene_template_exercise,
        descResId = R.string.scene_wizard_exercise_desc,
        infoResId = R.string.scene_wizard_info_exercise,
        icon = "exercise",
        defaultDurationMinutes = 180, // 3 hours
        defaultActions = listOf(
            SceneAction.TempTarget(reason = TT.Reason.ACTIVITY, targetMgdl = 140.0),
            SceneAction.ProfileSwitch(profileName = "", percentage = 70),
            SceneAction.SmbToggle(enabled = false),
            SceneAction.CarePortalEvent(type = TE.Type.EXERCISE)
        )
    ),

    SICK_DAY(
        nameResId = R.string.scene_template_sick_day,
        descResId = R.string.scene_wizard_sick_day_desc,
        infoResId = R.string.scene_wizard_info_sick_day,
        icon = "thermostat",
        defaultDurationMinutes = 480, // 8 hours
        defaultActions = listOf(
            SceneAction.ProfileSwitch(profileName = "", percentage = 150),
            SceneAction.CarePortalEvent(type = TE.Type.SICKNESS)
        )
    ),

    SICK_DAY_VOMITING(
        nameResId = R.string.scene_template_sick_day_vomiting,
        descResId = R.string.scene_wizard_sick_day_vomiting_desc,
        infoResId = R.string.scene_wizard_info_sick_day_vomiting,
        icon = "thermostat",
        defaultDurationMinutes = 480, // 8 hours
        defaultActions = listOf(
            SceneAction.ProfileSwitch(profileName = "", percentage = 60),
            SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS),
            SceneAction.SmbToggle(enabled = false),
            SceneAction.CarePortalEvent(type = TE.Type.SICKNESS)
        )
    ),

    SLEEP(
        nameResId = R.string.scene_template_sleep,
        descResId = R.string.scene_wizard_sleep_desc,
        infoResId = R.string.scene_wizard_info_sleep,
        icon = "sleep",
        defaultDurationMinutes = 480, // 8 hours
        defaultActions = emptyList()
    ),

    POST_EXERCISE_NIGHT(
        nameResId = R.string.scene_template_post_exercise_night,
        descResId = R.string.scene_wizard_post_exercise_night_desc,
        infoResId = R.string.scene_wizard_info_post_exercise_night,
        icon = "sleep",
        defaultDurationMinutes = 600, // 10 hours — covers sleep + post-exercise safety buffer
        defaultActions = listOf(
            SceneAction.ProfileSwitch(profileName = "", percentage = 75),
            SceneAction.TempTarget(reason = TT.Reason.HYPOGLYCEMIA, targetMgdl = 120.0),
            SceneAction.SmbToggle(enabled = false)
        )
    ),

    PRE_MEAL(
        nameResId = R.string.scene_template_pre_meal,
        descResId = R.string.scene_wizard_pre_meal_desc,
        infoResId = R.string.scene_wizard_info_pre_meal,
        icon = "meal",
        defaultDurationMinutes = 30,
        defaultActions = listOf(
            SceneAction.TempTarget(reason = TT.Reason.EATING_SOON, targetMgdl = 90.0)
        )
    ),

    ALCOHOL(
        nameResId = R.string.scene_template_alcohol,
        descResId = R.string.scene_wizard_alcohol_desc,
        infoResId = R.string.scene_wizard_info_alcohol,
        icon = "cafe",
        defaultDurationMinutes = 720, // 12 hours
        defaultActions = listOf(
            SceneAction.TempTarget(reason = TT.Reason.HYPOGLYCEMIA, targetMgdl = 120.0),
            SceneAction.ProfileSwitch(profileName = "", percentage = 80),
            SceneAction.LoopModeChange(mode = RM.Mode.CLOSED_LOOP_LGS),
            SceneAction.SmbToggle(enabled = false),
            SceneAction.CarePortalEvent(type = TE.Type.ALCOHOL)
        )
    ),

    DRIVING(
        nameResId = R.string.scene_template_driving,
        descResId = R.string.scene_wizard_driving_desc,
        infoResId = R.string.scene_wizard_info_driving,
        icon = "car",
        defaultDurationMinutes = 60,
        defaultActions = listOf(
            SceneAction.TempTarget(reason = TT.Reason.HYPOGLYCEMIA, targetMgdl = 108.0)
        )
    ),

    BATHING(
        nameResId = R.string.scene_template_bathing,
        descResId = R.string.scene_wizard_bathing_desc,
        infoResId = R.string.scene_wizard_info_bathing,
        icon = "swim",
        defaultDurationMinutes = 60,
        defaultActions = listOf(
            SceneAction.LoopModeChange(mode = RM.Mode.DISCONNECTED_PUMP)
        )
    ),

    LUTEAL_PHASE(
        nameResId = R.string.scene_template_luteal_phase,
        descResId = R.string.scene_wizard_luteal_phase_desc,
        infoResId = R.string.scene_wizard_info_luteal_phase,
        icon = "heart",
        defaultDurationMinutes = 7200, // 5 days
        defaultActions = listOf(
            SceneAction.ProfileSwitch(profileName = "", percentage = 115),
            SceneAction.CarePortalEvent(type = TE.Type.PRE_PERIOD)
        )
    ),

    HOT_WEATHER(
        nameResId = R.string.scene_template_hot_weather,
        descResId = R.string.scene_wizard_hot_weather_desc,
        infoResId = R.string.scene_wizard_info_hot_weather,
        icon = "thermostat",
        defaultDurationMinutes = 480, // 8 hours
        defaultActions = listOf(
            SceneAction.ProfileSwitch(profileName = "", percentage = 85)
        )
    ),

    MEDICAL_PROCEDURE(
        nameResId = R.string.scene_template_medical_procedure,
        descResId = R.string.scene_wizard_medical_procedure_desc,
        infoResId = R.string.scene_wizard_info_medical_procedure,
        icon = "hospital",
        defaultDurationMinutes = 0, // indefinite — end manually
        defaultActions = listOf(
            SceneAction.LoopModeChange(mode = RM.Mode.OPEN_LOOP),
            SceneAction.SmbToggle(enabled = false)
        )
    ),

    BLANK(
        nameResId = R.string.scene_template_blank,
        descResId = R.string.scene_wizard_blank_desc,
        infoResId = 0, // No info step for blank template
        icon = "star",
        defaultDurationMinutes = 60,
        defaultActions = emptyList()
    )
}
