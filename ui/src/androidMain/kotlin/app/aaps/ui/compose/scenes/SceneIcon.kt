package app.aaps.ui.compose.scenes

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AirlineSeatFlat
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ScubaDiving
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Skateboarding
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsHandball
import androidx.compose.material.icons.filled.SportsRugby
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Surfing
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcActivity
import app.aaps.core.ui.compose.icons.IcAutomation
import app.aaps.core.ui.compose.icons.IcBolus
import app.aaps.core.ui.compose.icons.IcCalibration
import app.aaps.core.ui.compose.icons.IcCarbs
import app.aaps.core.ui.compose.icons.IcLoopClosed
import app.aaps.core.ui.compose.icons.IcNote
import app.aaps.core.ui.compose.icons.IcProfile
import app.aaps.core.ui.compose.icons.IcSmb
import app.aaps.core.ui.compose.icons.IcTbrHigh
import app.aaps.core.ui.compose.icons.IcTtActivity
import app.aaps.core.ui.compose.icons.IcTtEatingSoon
import app.aaps.core.ui.compose.icons.IcTtHigh
import app.aaps.core.ui.compose.icons.IcTtHypo
import app.aaps.core.ui.compose.icons.IcTtManual
import app.aaps.ui.R
import app.aaps.ui.UiStrings

/**
 * Available icons for scenes, organized by category.
 * AAPS-specific icons first, then Material icons by theme.
 */
data class SceneIconEntry(
    val key: String,
    val icon: ImageVector,
    val label: TextRef
)

data class SceneIconCategory(
    val name: TextRef,
    val icons: List<SceneIconEntry>
)

object SceneIcons {

    // --- AAPS-specific ---
    private val TT_HIGH = SceneIconEntry("aaps_tt_high", IcTtHigh, UiStrings.aaps_tt_high_icon_label)
    private val TT_ACTIVITY = SceneIconEntry("aaps_tt_activity", IcTtActivity, UiStrings.aaps_tt_activity_icon_label)
    private val TT_EATING = SceneIconEntry("aaps_tt_eating", IcTtEatingSoon, UiStrings.aaps_tt_eating_icon_label)
    private val TT_HYPO = SceneIconEntry("aaps_tt_hypo", IcTtHypo, UiStrings.aaps_tt_hypo_icon_label)
    private val TT_MANUAL = SceneIconEntry("aaps_tt_manual", IcTtManual, UiStrings.aaps_tt_manual_icon_label)
    private val PROFILE = SceneIconEntry("aaps_profile", IcProfile, UiStrings.aaps_profile_icon_label)
    private val LOOP = SceneIconEntry("aaps_loop", IcLoopClosed, UiStrings.aaps_loop_icon_label)
    private val SMB = SceneIconEntry("aaps_smb", IcSmb, UiStrings.aaps_smb_icon_label)
    private val BOLUS = SceneIconEntry("aaps_bolus", IcBolus, UiStrings.aaps_bolus_icon_label)
    private val CARBS = SceneIconEntry("aaps_carbs", IcCarbs, UiStrings.aaps_carbs_icon_label)
    private val TBR = SceneIconEntry("aaps_tbr", IcTbrHigh, UiStrings.aaps_tbr_icon_label)
    private val ACTIVITY_AAPS = SceneIconEntry("aaps_activity", IcActivity, UiStrings.aaps_activity_icon_label)
    private val NOTE = SceneIconEntry("aaps_note", IcNote, UiStrings.aaps_note_icon_label)
    private val AUTOMATION = SceneIconEntry("aaps_automation", IcAutomation, UiStrings.aaps_automation_icon_label)
    private val CALIBRATION = SceneIconEntry("aaps_calibration", IcCalibration, UiStrings.aaps_calibration_icon_label)

    // --- Material: Activity ---
    private val EXERCISE = SceneIconEntry("exercise", Icons.AutoMirrored.Filled.DirectionsRun, UiStrings.exercise_icon_label)
    private val FITNESS = SceneIconEntry("fitness", Icons.Default.FitnessCenter, UiStrings.fitness_icon_label)
    private val POOL = SceneIconEntry("swim", Icons.Default.Pool, UiStrings.swim_icon_label)
    private val HIKING = SceneIconEntry("hiking", Icons.Default.Hiking, UiStrings.hiking_icon_label)
    private val BIKE = SceneIconEntry("bike", Icons.AutoMirrored.Filled.DirectionsBike, UiStrings.bike_icon_label)
    private val SKATEBOARD = SceneIconEntry("skateboard", Icons.Default.Skateboarding, UiStrings.skateboard_icon_label)
    private val TENNIS = SceneIconEntry("tennis", Icons.Default.SportsTennis, UiStrings.tennis_icon_label)
    private val SOCCER = SceneIconEntry("soccer", Icons.Default.SportsSoccer, UiStrings.soccer_icon_label)
    private val RUGBY = SceneIconEntry("baseball", Icons.Default.SportsRugby, UiStrings.baseball_icon_label)
    private val HANDBALL = SceneIconEntry("basketball", Icons.Default.SportsHandball, UiStrings.basketball_icon_label)
    private val GYMNASTICS = SceneIconEntry("volleyball", Icons.Default.SportsGymnastics, UiStrings.volleyball_icon_label)
    private val GOLF = SceneIconEntry("golf", Icons.Default.SportsGolf, UiStrings.golf_icon_label)
    private val SCUBADIVING = SceneIconEntry("scubadiving", Icons.Default.ScubaDiving, UiStrings.scubadiving_icon_label)
    private val ROWING = SceneIconEntry("rowing", Icons.Default.Rowing, UiStrings.rowing_icon_label)
    private val SURFING = SceneIconEntry("surfing", Icons.Default.Surfing, UiStrings.surfing_icon_label)

    // --- Material: Health ---
    private val HOSPITAL = SceneIconEntry("hospital", Icons.Default.LocalHospital, UiStrings.hospital_icon_label)
    private val MEDICATION = SceneIconEntry("medication", Icons.Default.Medication, UiStrings.medication_icon_label)
    private val MONITOR_HEART = SceneIconEntry("monitor_heart", Icons.Default.MonitorHeart, UiStrings.monitor_heart_icon_label)
    private val THERMOSTAT = SceneIconEntry("thermostat", Icons.Default.Thermostat, UiStrings.thermostat_icon_label)
    private val BLOOD_TYPE = SceneIconEntry("blood_type", Icons.Default.Bloodtype, UiStrings.blood_type_icon_label)
    private val HEALTH_SAFETY = SceneIconEntry("health_safety", Icons.Default.HealthAndSafety, UiStrings.health_safety_icon_label)
    private val HEALING = SceneIconEntry("healing", Icons.Default.Healing, UiStrings.healing_icon_label)

    // --- Material: Food ---
    private val MEAL = SceneIconEntry("meal", Icons.Default.Restaurant, UiStrings.meal_icon_label)
    private val CAFE = SceneIconEntry("cafe", Icons.Default.LocalCafe, UiStrings.cafe_icon_label)
    private val LUNCH = SceneIconEntry("lunch", Icons.Default.LunchDining, UiStrings.lunch_icon_label)
    private val BAKERY = SceneIconEntry("bakery", Icons.Default.BakeryDining, UiStrings.bakery_icon_label)

    // --- Material: Sleep ---
    private val SLEEP = SceneIconEntry("sleep", Icons.Default.Bedtime, UiStrings.sleep_icon_label)
    private val NIGHT = SceneIconEntry("night", Icons.Default.Nightlight, UiStrings.night_icon_label)
    private val NIGHTS_STAY = SceneIconEntry("nights_stay", Icons.Default.NightsStay, UiStrings.nights_stay_icon_label)
    private val HOTEL = SceneIconEntry("hotel", Icons.Default.Hotel, UiStrings.hotel_icon_label)
    private val SEAT_FLAT = SceneIconEntry("seat_flat", Icons.Default.AirlineSeatFlat, UiStrings.seat_flat_icon_label)

    // --- Material: Daily Life ---
    private val WORK = SceneIconEntry("work", Icons.Default.Work, UiStrings.work_icon_label)
    private val SCHOOL = SceneIconEntry("school", Icons.Default.School, UiStrings.school_icon_label)
    private val HOME = SceneIconEntry("home", Icons.Default.Home, UiStrings.home_icon_label)
    private val CAR = SceneIconEntry("car", Icons.Default.DirectionsCar, UiStrings.car_icon_label)
    private val FLIGHT = SceneIconEntry("flight", Icons.Default.Flight, UiStrings.flight_icon_label)
    private val SHOPPING = SceneIconEntry("shopping", Icons.Default.ShoppingCart, UiStrings.shopping_icon_label)

    // --- Material: Wellness ---
    private val RELAX = SceneIconEntry("relax", Icons.Default.SelfImprovement, UiStrings.relax_icon_label)
    private val SPA = SceneIconEntry("spa", Icons.Default.Spa, UiStrings.spa_icon_label)
    private val HEART = SceneIconEntry("heart", Icons.Default.Favorite, UiStrings.heart_icon_label)
    private val PSYCHOLOGY = SceneIconEntry("psychology", Icons.Default.Psychology, UiStrings.psychology_icon_label)

    // --- Material: General ---
    private val STAR = SceneIconEntry("star", Icons.Default.Star, UiStrings.star_icon_label)
    private val SPEED = SceneIconEntry("speed", Icons.Default.Speed, UiStrings.speed_icon_label)
    private val ALARM = SceneIconEntry("alarm", Icons.Default.Alarm, UiStrings.alarm_icon_label)
    private val EVENT = SceneIconEntry("event", Icons.Default.Event, UiStrings.event_icon_label)
    private val FLAG = SceneIconEntry("flag", Icons.Default.Flag, UiStrings.flag_icon_label)
    private val BOOKMARK = SceneIconEntry("bookmark", Icons.Default.Bookmark, UiStrings.bookmark_icon_label)
    private val LABEL = SceneIconEntry("label", Icons.AutoMirrored.Filled.Label, UiStrings.label_icon_label)

    /** All icons in a flat list */
    private val allIcons: List<SceneIconEntry> = listOf(
        // AAPS
        TT_HIGH, TT_ACTIVITY, TT_EATING, TT_HYPO, TT_MANUAL, PROFILE, LOOP, SMB, BOLUS, CARBS,
        TBR, ACTIVITY_AAPS, NOTE, AUTOMATION, CALIBRATION,
        // Material
        EXERCISE, FITNESS, POOL, HIKING, BIKE, SKATEBOARD, TENNIS, SOCCER, RUGBY, HANDBALL, GYMNASTICS, GOLF, SCUBADIVING, ROWING, SURFING,
        HOSPITAL, MEDICATION, MONITOR_HEART, THERMOSTAT, BLOOD_TYPE, HEALTH_SAFETY, HEALING,
        MEAL, CAFE, LUNCH, BAKERY,
        SLEEP, NIGHT, NIGHTS_STAY, HOTEL, SEAT_FLAT,
        WORK, SCHOOL, HOME, CAR, FLIGHT, SHOPPING,
        RELAX, SPA, HEART, PSYCHOLOGY,
        STAR, SPEED, ALARM, EVENT, FLAG, BOOKMARK, LABEL
    )

    private val byKey: Map<String, SceneIconEntry> = allIcons.associateBy { it.key }

    /** Resolve icon by persisted key, fallback to Star */
    fun fromKey(key: String): SceneIconEntry = byKey[key] ?: STAR

    /** All icons organized by category for the picker UI */
    val categories: List<SceneIconCategory> = listOf(
        SceneIconCategory(
            UiStrings.scene_cat_aaps, listOf(
                TT_HIGH, TT_ACTIVITY, TT_EATING, TT_HYPO, TT_MANUAL, PROFILE, LOOP, SMB, BOLUS, CARBS,
                TBR, ACTIVITY_AAPS, NOTE, AUTOMATION, CALIBRATION
            )
        ),
        SceneIconCategory(UiStrings.scene_cat_activity, listOf(EXERCISE, FITNESS, POOL, HIKING, BIKE, SKATEBOARD, TENNIS, SOCCER, RUGBY, HANDBALL, GYMNASTICS, GOLF, SCUBADIVING, ROWING, SURFING)),
        SceneIconCategory(UiStrings.scene_cat_health, listOf(HOSPITAL, MEDICATION, MONITOR_HEART, THERMOSTAT, BLOOD_TYPE, HEALTH_SAFETY, HEALING)),
        SceneIconCategory(UiStrings.scene_cat_food, listOf(MEAL, CAFE, LUNCH, BAKERY)),
        SceneIconCategory(UiStrings.scene_cat_sleep, listOf(SLEEP, NIGHT, NIGHTS_STAY, HOTEL, SEAT_FLAT)),
        SceneIconCategory(UiStrings.scene_cat_dailylife, listOf(WORK, SCHOOL, HOME, CAR, FLIGHT, SHOPPING)),
        SceneIconCategory(UiStrings.scene_cat_wellness, listOf(RELAX, SPA, HEART, PSYCHOLOGY)),
        SceneIconCategory(UiStrings.scene_cat_general, listOf(STAR, SPEED, ALARM, EVENT, FLAG, BOOKMARK, LABEL))
    )
}
