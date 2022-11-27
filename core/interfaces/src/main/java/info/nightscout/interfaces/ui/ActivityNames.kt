package info.nightscout.interfaces.ui

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx))
 */
interface ActivityNames {

    val mainActivity: Class<*>
    val tddStatsActivity: Class<*>
    val historyBrowseActivity: Class<*>
    val errorHelperActivity: Class<*>
    val bolusProgressHelperActivity: Class<*>
    val singleFragmentActivity: Class<*>
    val preferencesActivity: Class<*>
    val myPreferenceFragment: Class<*>

    val prefGeneral: Int
    /**
     * Show ErrorHelperActivity and start alarm
     * @param ctx Context
     * @param status message inside dialog
     * @param title title of dialog
     * @param soundId sound resource. if == 0 alarm is not started
     */
    fun runAlarm(ctx: Context, status: String, title: String, @RawRes soundId: Int = 0)
    fun runWizardDialog(fragmentManager: FragmentManager, carbs: Int? = null, name: String? = null)
    fun runLoopDialog(fragmentManager: FragmentManager, showOkCancel: Int)
    fun runProfileSwitchDialog(fragmentManager: FragmentManager, profileName: String? = null)
    fun runTempBasalDialog(fragmentManager: FragmentManager)
    fun runTreatmentDialog(fragmentManager: FragmentManager)
    fun runInsulinDialog(fragmentManager: FragmentManager)
    fun runCalibrationDialog(fragmentManager: FragmentManager)
    fun runCarbsDialog(fragmentManager: FragmentManager)
    fun runTempTargetDialog(fragmentManager: FragmentManager)
    fun runExtendedBolusDialog(fragmentManager: FragmentManager)
    fun runFillDialog(fragmentManager: FragmentManager)
    fun runBolusProgressDialog(fragmentManager: FragmentManager, insulin: Double, id: Long)
    enum class Mode(val i: Int) {
        RUNNING_PROFILE(1),
        CUSTOM_PROFILE(2),
        DB_PROFILE(3),
        PROFILE_COMPARE(4)
    }
    fun runProfileViewerDialog(fragmentManager: FragmentManager, time: Long, mode: Mode, customProfile: String?= null, customProfileName: String? = null, customProfile2: String? = null)
    enum class EventType {
        BGCHECK,
        SENSOR_INSERT,
        BATTERY_CHANGE,
        NOTE,
        EXERCISE,
        QUESTION,
        ANNOUNCEMENT
    }

    fun runCareDialog(fragmentManager: FragmentManager, options: EventType, @StringRes event: Int)

    fun addNotification(id: Int, text: String, level: Int)
    fun addNotificationValidFor(id: Int, text: String, level: Int, validMinutes: Int)
    fun addNotificationWithSound(id: Int, text: String, level: Int, @RawRes soundId: Int)
    fun addNotificationValidTo(id: Int, date: Long, text: String, level: Int, validTo: Long)
}