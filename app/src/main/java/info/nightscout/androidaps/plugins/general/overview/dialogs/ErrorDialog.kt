package info.nightscout.androidaps.plugins.general.overview.dialogs


import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.services.AlarmSoundService
import kotlinx.android.synthetic.main.overview_error_dialog.*
import org.slf4j.LoggerFactory

class ErrorDialog : DialogFragment() {
    private val log = LoggerFactory.getLogger(ErrorDialog::class.java)

    var helperActivity: ErrorHelperActivity? = null
    var status: String = ""
    var title: String = ""
    var sound: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.setTitle(title)
        isCancelable = false

        savedInstanceState?.let { bundle ->
            bundle.getString("status")?.let { status = it }
            bundle.getString("title")?.let { title = it }
            sound = bundle.getInt("sound", R.raw.error)
        }
        return inflater.inflate(R.layout.overview_error_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        overview_error_ok.setOnClickListener {
            log.debug("Error dialog ok button pressed")
            dismiss()
        }
        overview_error_mute.setOnClickListener {
            log.debug("Error dialog mute button pressed")
            stopAlarm()
        }
        startAlarm()
    }

    override fun onSaveInstanceState(bundle: Bundle) {
        super.onSaveInstanceState(bundle)
        bundle.putString("status", status)
        bundle.putString("title", title)
        bundle.putInt("sound", sound)
    }

    override fun onResume() {
        super.onResume()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        overview_error_status.text = status
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        helperActivity?.finish()
        stopAlarm()
    }

    private fun startAlarm() {
        if (sound != 0) {
            val alarm = Intent(MainApp.instance().applicationContext, AlarmSoundService::class.java)
            alarm.putExtra("soundid", sound)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                MainApp.instance().startForegroundService(alarm)
            } else {
                MainApp.instance().startService(alarm)
            }
        }
    }

    private fun stopAlarm() =
            MainApp.instance().stopService(Intent(MainApp.instance().applicationContext, AlarmSoundService::class.java))
}
