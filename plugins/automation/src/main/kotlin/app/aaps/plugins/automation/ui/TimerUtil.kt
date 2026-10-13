package app.aaps.plugins.automation.ui

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.automation.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton class TimerUtil @Inject constructor(
    private val context: Context
) {

    /**
     * Schedule alarm in android
     *
     * @param seconds
     */
    fun scheduleReminder(seconds: Int, text: String) {
        try {
            Intent(AlarmClock.ACTION_SET_TIMER).apply {
                flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                putExtra(AlarmClock.EXTRA_MESSAGE, text)
                context.startActivity(this)
            }
        } catch (_: Exception) {
            ToastUtils.errorToast(context, R.string.error_setting_reminder)
        }
    }
}