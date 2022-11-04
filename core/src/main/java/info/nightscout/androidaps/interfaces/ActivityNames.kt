package info.nightscout.androidaps.interfaces

import android.content.Context
import androidx.annotation.RawRes
import androidx.fragment.app.FragmentManager

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx))
 */
interface ActivityNames {

    val mainActivityClass: Class<*>
    val tddStatsActivity: Class<*>
    val errorHelperActivity: Class<*>
    val bolusProgressHelperActivity: Class<*>
    val singleFragmentActivity: Class<*>

    /**
     * Show ErrorHelperActivity and start alarm
     * @param ctx Context
     * @param status message inside dialog
     * @param title title of dialog
     * @param soundId sound resource. if == 0 alarm is not started
     */
    fun runAlarm(ctx: Context, status: String, title: String, @RawRes soundId: Int = 0)
    fun runWizard(fragmentManager: FragmentManager, carbs: Int, name: String)
    fun runProfileSwitchDialog(fragmentManager: FragmentManager, profileName: String?)
}