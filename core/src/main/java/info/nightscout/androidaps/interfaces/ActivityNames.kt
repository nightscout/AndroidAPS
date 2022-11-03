package info.nightscout.androidaps.interfaces

import android.content.Context
import androidx.annotation.RawRes

/**
 * Interface to use activities located in different modules
 * usage: startActivity(Intent(context, activityNames.xxxx))
 */
interface ActivityNames {

    val mainActivityClass: Class<*>
    val tddStatsActivity: Class<*>
    val errorHelperActivity: Class<*>
    val bolusProgressHelperActivity: Class<*>

    /**
     * Show ErrorHelperActivity and start alarm
     * @param ctx Context
     * @param status message inside dialog
     * @param title title of dialog
     * @param soundId sound resource. if == 0 alarm is not started
     */
    fun runAlarm(ctx: Context, status: String, title: String, @RawRes soundId: Int = 0)
}