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
    private val resourceHelper: ResourceHelper,
    private val dateUtil: DateUtil
) {

    fun scheduleReminder(time: Long, text: String? = null) {
        Intent(AlarmClock.ACTION_SET_TIMER).apply {
            val length: Int = ((time - dateUtil.now()) / 1000).toInt()
            flags = flags or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(AlarmClock.EXTRA_LENGTH, length)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            putExtra(AlarmClock.EXTRA_MESSAGE, text ?: resourceHelper.gs(R.string.app_name))
            context.startActivity(this)
        }
    }
}