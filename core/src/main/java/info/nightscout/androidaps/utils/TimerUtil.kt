package info.nightscout.androidaps.utils

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimerUtil @Inject constructor(private val context: Context) {

    /**
     * Schedule alarm in @seconds
     */
    fun scheduleReminder(seconds: Long, text: String) {
        Intent(AlarmClock.ACTION_SET_TIMER).apply {
            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AlarmClock.EXTRA_LENGTH, seconds.toTimerSeconds())
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, text)
            context.startActivity(this)
        }
    }
}

private fun Long.toTimerSeconds() = coerceIn(0L, Integer.MAX_VALUE.toLong()).toInt()
