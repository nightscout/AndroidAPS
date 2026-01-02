package app.aaps.core.interfaces.ui

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentManager
import app.aaps.core.interfaces.nsclient.NSAlarm

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx))
 */
interface UiInteraction {

    val mainActivity: Class<*>
    val tddStatsActivity: Class<*>
    val historyBrowseActivity: Class<*>
    val errorHelperActivity: Class<*>
    val bolusProgressHelperActivity: Class<*>
    val singleFragmentActivity: Class<*>
    val preferencesActivity: Class<*>
    val myPreferenceFragment: Class<*>
    val quickWizardListActivity: Class<*>

    companion object {

        const val PLUGIN_NAME = "PluginName"

        /**
         * Preference from [Preferences]
         */
        const val PREFERENCE = "Preference"
    }

    enum class Preferences { PROTECTION }

    /**
     * Arrays for preferences
     */
    val unitsEntries: Array<CharSequence>
    val unitsValues: Array<CharSequence>

    /**
     * Show ErrorHelperActivity and start alarm
     * @param ctx Context
     * @param status message inside dialog
     * @param title title of dialog
     * @param soundId sound resource. if == 0 alarm is not started
     */
    fun runAlarm(status: String, title: String, @RawRes soundId: Int = 0)

    fun updateWidget(context: Context, from: String)

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
    enum class SiteMode(val i: Int) {
        VIEW(1),
        EDIT(2)
    }
    fun runSiteRotationDialog(fragmentManager: FragmentManager)
    fun runBolusProgressDialog(fragmentManager: FragmentManager)
    enum class Mode(val i: Int) {
        RUNNING_PROFILE(1),
        CUSTOM_PROFILE(2),
        DB_PROFILE(3),
        PROFILE_COMPARE(4)
    }

    fun runProfileViewerDialog(fragmentManager: FragmentManager, time: Long, mode: Mode, customProfile: String? = null, customProfileName: String? = null, customProfile2: String? = null)
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

    /**
     * Remove notification
     * @param id if of notification
     */
    fun dismissNotification(id: Int)
    fun addNotification(id: Int, text: String, level: Int)
    fun addNotificationValidFor(id: Int, text: String, level: Int, validMinutes: Int)
    fun addNotificationWithSound(id: Int, text: String, level: Int, @RawRes soundId: Int?)
    fun addNotificationValidTo(id: Int, date: Long, text: String, level: Int, validTo: Long)
    fun addNotificationWithAction(nsAlarm: NSAlarm)
    fun addNotificationWithAction(id: Int, text: String, level: Int, @StringRes buttonText: Int, action: Runnable, validityCheck: (() -> Boolean)?, @RawRes soundId: Int? = null, date: Long = System.currentTimeMillis(), validTo: Long = 0)

    /**
     * Add notification that shows dialog after clicking button
     * @param id if of notification
     * @text text of notification
     * @level urgency level of notification
     * @actionButtonId label of button
     * @title Dialog title
     * @message Dialog body
     */
    fun addNotificationWithDialogResponse(id: Int, text: String, level: Int, @StringRes buttonText: Int, title: String, message: String, validityCheck: (() -> Boolean)?)

    /**
     * Add notification that executes [Runnable] after clicking button
     * @param id if of notification
     * @text text of notification
     * @level urgency level of notification
     * @actionButtonId label of button
     * @action Runnable to be run
     */
    fun addNotification(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, action: Runnable, validityCheck: (() -> Boolean)?)

    fun showToastAndNotification(ctx: Context, string: String, @RawRes soundID: Int)

    fun startAlarm(@RawRes sound: Int, reason: String)
    fun stopAlarm(reason: String)
}