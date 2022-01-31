package info.nightscout.androidaps.utils

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.utils.resources.ResourceHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerUtil @Inject constructor(
    private val context: Context,
    private val rh: ResourceHelper,
    private val dateUtil: DateUtil
) {

    /**
     * Schedule alarm in @seconds
     */
    fun scheduleReminder(seconds: Int, text: String) {
        Intent(AlarmClock.ACTION_SET_TIMER).apply {
            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, text)
            context.startActivity(this)
        }
    }
}