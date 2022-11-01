package info.nightscout.androidaps.utils

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerUtil @Inject constructor(
    private val context: Context
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