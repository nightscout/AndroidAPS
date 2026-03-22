package app.aaps.ui.compose.scenes

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
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Skateboarding
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
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

/**
 * Available icons for scenes, organized by category.
 * AAPS-specific icons first, then Material icons by theme.
 */
data class SceneIconEntry(
    val key: String,
    val icon: ImageVector,
    val label: String
)

data class SceneIconCategory(
    val name: String,
    val icons: List<SceneIconEntry>
)

object SceneIcons {

    // --- AAPS-specific ---
    private val TT_HIGH = SceneIconEntry("aaps_tt_high", IcTtHigh, "Temp Target")
    private val TT_ACTIVITY = SceneIconEntry("aaps_tt_activity", IcTtActivity, "TT Activity")
    private val TT_EATING = SceneIconEntry("aaps_tt_eating", IcTtEatingSoon, "TT Eating Soon")
    private val TT_HYPO = SceneIconEntry("aaps_tt_hypo", IcTtHypo, "TT Hypo")
    private val TT_MANUAL = SceneIconEntry("aaps_tt_manual", IcTtManual, "TT Manual")
    private val PROFILE = SceneIconEntry("aaps_profile", IcProfile, "Profile")
    private val LOOP = SceneIconEntry("aaps_loop", IcLoopClosed, "Loop")
    private val SMB = SceneIconEntry("aaps_smb", IcSmb, "SMB")
    private val BOLUS = SceneIconEntry("aaps_bolus", IcBolus, "Bolus")
    private val CARBS = SceneIconEntry("aaps_carbs", IcCarbs, "Carbs")
    private val TBR = SceneIconEntry("aaps_tbr", IcTbrHigh, "Temp Basal")
    private val ACTIVITY_AAPS = SceneIconEntry("aaps_activity", IcActivity, "Exercise")
    private val NOTE = SceneIconEntry("aaps_note", IcNote, "Note")
    private val AUTOMATION = SceneIconEntry("aaps_automation", IcAutomation, "Automation")
    private val CALIBRATION = SceneIconEntry("aaps_calibration", IcCalibration, "Calibration")

    // --- Material: Activity ---
    private val EXERCISE = SceneIconEntry("exercise", Icons.AutoMirrored.Filled.DirectionsRun, "Run")
    private val FITNESS = SceneIconEntry("fitness", Icons.Default.FitnessCenter, "Fitness")
    private val POOL = SceneIconEntry("swim", Icons.Default.Pool, "Swim")
    private val HIKING = SceneIconEntry("hiking", Icons.Default.Hiking, "Hiking")
    private val BIKE = SceneIconEntry("bike", Icons.AutoMirrored.Filled.DirectionsBike, "Bike")
    private val SKATEBOARD = SceneIconEntry("skateboard", Icons.Default.Skateboarding, "Skateboard")

    // --- Material: Health ---
    private val HOSPITAL = SceneIconEntry("hospital", Icons.Default.LocalHospital, "Hospital")
    private val MEDICATION = SceneIconEntry("medication", Icons.Default.Medication, "Medication")
    private val MONITOR_HEART = SceneIconEntry("monitor_heart", Icons.Default.MonitorHeart, "Heart Monitor")
    private val THERMOSTAT = SceneIconEntry("thermostat", Icons.Default.Thermostat, "Thermostat")
    private val BLOOD_TYPE = SceneIconEntry("blood_type", Icons.Default.Bloodtype, "Blood")
    private val HEALTH_SAFETY = SceneIconEntry("health_safety", Icons.Default.HealthAndSafety, "Health")
    private val HEALING = SceneIconEntry("healing", Icons.Default.Healing, "Healing")

    // --- Material: Food ---
    private val MEAL = SceneIconEntry("meal", Icons.Default.Restaurant, "Restaurant")
    private val CAFE = SceneIconEntry("cafe", Icons.Default.LocalCafe, "Coffee")
    private val LUNCH = SceneIconEntry("lunch", Icons.Default.LunchDining, "Lunch")
    private val BAKERY = SceneIconEntry("bakery", Icons.Default.BakeryDining, "Bakery")

    // --- Material: Sleep ---
    private val SLEEP = SceneIconEntry("sleep", Icons.Default.Bedtime, "Sleep")
    private val NIGHT = SceneIconEntry("night", Icons.Default.Nightlight, "Night")
    private val NIGHTS_STAY = SceneIconEntry("nights_stay", Icons.Default.NightsStay, "Nights Stay")
    private val HOTEL = SceneIconEntry("hotel", Icons.Default.Hotel, "Hotel")
    private val SEAT_FLAT = SceneIconEntry("seat_flat", Icons.Default.AirlineSeatFlat, "Rest")

    // --- Material: Daily Life ---
    private val WORK = SceneIconEntry("work", Icons.Default.Work, "Work")
    private val SCHOOL = SceneIconEntry("school", Icons.Default.School, "School")
    private val HOME = SceneIconEntry("home", Icons.Default.Home, "Home")
    private val CAR = SceneIconEntry("car", Icons.Default.DirectionsCar, "Drive")
    private val FLIGHT = SceneIconEntry("flight", Icons.Default.Flight, "Flight")
    private val SHOPPING = SceneIconEntry("shopping", Icons.Default.ShoppingCart, "Shopping")

    // --- Material: Wellness ---
    private val RELAX = SceneIconEntry("relax", Icons.Default.SelfImprovement, "Meditate")
    private val SPA = SceneIconEntry("spa", Icons.Default.Spa, "Spa")
    private val HEART = SceneIconEntry("heart", Icons.Default.Favorite, "Heart")
    private val PSYCHOLOGY = SceneIconEntry("psychology", Icons.Default.Psychology, "Mind")

    // --- Material: General ---
    private val STAR = SceneIconEntry("star", Icons.Default.Star, "Star")
    private val SPEED = SceneIconEntry("speed", Icons.Default.Speed, "Speed")
    private val ALARM = SceneIconEntry("alarm", Icons.Default.Alarm, "Alarm")
    private val EVENT = SceneIconEntry("event", Icons.Default.Event, "Event")
    private val FLAG = SceneIconEntry("flag", Icons.Default.Flag, "Flag")
    private val BOOKMARK = SceneIconEntry("bookmark", Icons.Default.Bookmark, "Bookmark")
    private val LABEL = SceneIconEntry("label", Icons.AutoMirrored.Filled.Label, "Label")

    /** All icons in a flat list */
    private val allIcons: List<SceneIconEntry> = listOf(
        // AAPS
        TT_HIGH, TT_ACTIVITY, TT_EATING, TT_HYPO, TT_MANUAL, PROFILE, LOOP, SMB, BOLUS, CARBS,
        TBR, ACTIVITY_AAPS, NOTE, AUTOMATION, CALIBRATION,
        // Material
        EXERCISE, FITNESS, POOL, HIKING, BIKE, SKATEBOARD,
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
            "AAPS", listOf(
                TT_HIGH, TT_ACTIVITY, TT_EATING, TT_HYPO, TT_MANUAL, PROFILE, LOOP, SMB, BOLUS, CARBS,
                TBR, ACTIVITY_AAPS, NOTE, AUTOMATION, CALIBRATION
            )
        ),
        SceneIconCategory("Activity", listOf(EXERCISE, FITNESS, POOL, HIKING, BIKE, SKATEBOARD)),
        SceneIconCategory("Health", listOf(HOSPITAL, MEDICATION, MONITOR_HEART, THERMOSTAT, BLOOD_TYPE, HEALTH_SAFETY, HEALING)),
        SceneIconCategory("Food", listOf(MEAL, CAFE, LUNCH, BAKERY)),
        SceneIconCategory("Sleep", listOf(SLEEP, NIGHT, NIGHTS_STAY, HOTEL, SEAT_FLAT)),
        SceneIconCategory("Daily Life", listOf(WORK, SCHOOL, HOME, CAR, FLIGHT, SHOPPING)),
        SceneIconCategory("Wellness", listOf(RELAX, SPA, HEART, PSYCHOLOGY)),
        SceneIconCategory("General", listOf(STAR, SPEED, ALARM, EVENT, FLAG, BOOKMARK, LABEL))
    )

}
