package app.aaps.implementations

import android.content.Context
import android.content.Intent
import androidx.annotation.RawRes
import app.aaps.MainActivity
import app.aaps.activities.HistoryBrowseActivity
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.configuration.activities.SingleFragmentActivity
import app.aaps.ui.activities.ErrorActivity
import app.aaps.ui.dialogs.AlertDialogs
import app.aaps.ui.services.AlarmSoundService
import app.aaps.ui.services.AlarmSoundServiceHelper
import app.aaps.ui.widget.Widget
import dagger.Reusable
import javax.inject.Inject

@Suppress("DEPRECATION")
@Reusable
class UiInteractionImpl @Inject constructor(
    private val context: Context,
    rxBus: RxBus,
    private val alarmSoundServiceHelper: AlarmSoundServiceHelper,
    preferences: Preferences
) : UiInteraction {

    private val alertDialogs: AlertDialogs = AlertDialogs(preferences, rxBus)

    override val mainActivity: Class<*> = MainActivity::class.java
    override val historyBrowseActivity: Class<*> = HistoryBrowseActivity::class.java
    override val errorHelperActivity: Class<*> = ErrorActivity::class.java
    override val singleFragmentActivity: Class<*> = SingleFragmentActivity::class.java

    override val unitsEntries = arrayOf<CharSequence>("mg/dL", "mmol/L")
    override val unitsValues = arrayOf<CharSequence>("mg/dl", "mmol")

    override fun runAlarm(status: String, title: String, @RawRes soundId: Int) {
        val i = Intent(context, errorHelperActivity)
        i.putExtra(AlarmSoundService.SOUND_ID, soundId)
        i.putExtra(AlarmSoundService.STATUS, status)
        i.putExtra(AlarmSoundService.TITLE, title)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    override fun updateWidget(context: Context, from: String) {
        Widget.updateWidget(context, from)
    }

    override fun startAlarm(@RawRes sound: Int, reason: String) {
        alarmSoundServiceHelper.startAlarm(sound, reason)
    }

    override fun stopAlarm(reason: String) {
        alarmSoundServiceHelper.stopAlarm(reason)
    }

    override fun showOkDialog(context: Context, title: String, message: String, onFinish: (() -> Unit)?) {
        alertDialogs.showOkDialog(context, title, message, onFinish)
    }

    override fun showOkDialog(context: Context, title: Int, message: Int, onFinish: (() -> Unit)?) {
        alertDialogs.showOkDialog(context, title, message, onFinish)
    }

    override fun showOkCancelDialog(context: Context, title: Int, message: Int, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, ok, cancel, icon)
    }

    override fun showOkCancelDialog(context: Context, title: String, message: String, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, ok, cancel, icon)
    }

    override fun showOkCancelDialog(context: Context, title: String, message: String, secondMessage: String, ok: (() -> Unit)?, cancel: (() -> Unit)?, icon: Int?) {
        alertDialogs.showOkCancelDialog(context, title, message, secondMessage, ok, cancel, icon)
    }

    override fun showYesNoCancel(context: Context, title: Int, message: Int, yes: (() -> Unit)?, no: (() -> Unit)?) {
        alertDialogs.showYesNoCancel(context, title, message, yes, no)
    }

    override fun showYesNoCancel(context: Context, title: String, message: String, yes: (() -> Unit)?, no: (() -> Unit)?) {
        alertDialogs.showYesNoCancel(context, title, message, yes, no)
    }

    override fun showError(context: Context, title: String, message: String, positiveButton: Int?, ok: (() -> Unit)?, cancel: (() -> Unit)?) {
        alertDialogs.showError(context, title, message, positiveButton, ok, cancel)
    }
}