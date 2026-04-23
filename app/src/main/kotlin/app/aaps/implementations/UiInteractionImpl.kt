package app.aaps.implementations

import android.content.Context
import android.content.Intent
import androidx.annotation.RawRes
import app.aaps.ComposeMainActivity
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.services.AlarmSoundService
import app.aaps.ui.services.AlarmSoundServiceHelper
import dagger.Reusable
import javax.inject.Inject

@Reusable
class UiInteractionImpl @Inject constructor(
    private val context: Context,
    private val alarmSoundServiceHelper: AlarmSoundServiceHelper
) : UiInteraction {

    override val mainActivity: Class<*> = ComposeMainActivity::class.java
    override val errorHelperActivity: Class<*> = ErrorActivity::class.java

    override fun runAlarm(status: String, title: String, @RawRes soundId: Int) {
        val i = Intent(context, errorHelperActivity)
        i.putExtra(AlarmSoundService.SOUND_ID, soundId)
        i.putExtra(AlarmSoundService.STATUS, status)
        i.putExtra(AlarmSoundService.TITLE, title)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    override fun startAlarm(@RawRes sound: Int, reason: String) {
        alarmSoundServiceHelper.startAlarm(sound, reason)
    }

    override fun stopAlarm(reason: String) {
        alarmSoundServiceHelper.stopAlarm(reason)
    }
}
