package info.nightscout.androidaps.dialogs

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.services.AlarmSoundService
import kotlinx.android.synthetic.main.dialog_error.*
import javax.inject.Inject

class ErrorDialog : DaggerDialogFragment() {
    @Inject lateinit var aapsLogger: AAPSLogger

    var helperActivity: ErrorHelperActivity? = null
    var status: String = ""
    var title: String = ""
    var sound: Int = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        savedInstanceState?.let { bundle ->
            bundle.getString("status")?.let { status = it }
            bundle.getString("title")?.let { title = it }
            sound = bundle.getInt("sound", R.raw.error)
        }
        aapsLogger.debug("Error dialog displayed")
        return inflater.inflate(R.layout.dialog_error, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        error_title.text = title
        overview_error_ok.setOnClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog ok button pressed")
            dismiss()
        }
        overview_error_mute.setOnClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog mute button pressed")
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

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        overview_error_status.text = status
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        helperActivity?.finish()
        stopAlarm()
    }

    private fun startAlarm() {
        if (sound != 0) {
            val alarm = Intent(context, AlarmSoundService::class.java)
            alarm.putExtra("soundid", sound)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context?.startForegroundService(alarm)
            } else {
                context?.startService(alarm)
            }
        }
    }

    private fun stopAlarm() =
        context?.stopService(Intent(context, AlarmSoundService::class.java))
}
