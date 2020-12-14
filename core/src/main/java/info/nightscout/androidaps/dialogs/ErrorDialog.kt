package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.core.databinding.DialogErrorBinding
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.services.AlarmSoundServiceHelper
import info.nightscout.androidaps.utils.T
import javax.inject.Inject

class ErrorDialog : DaggerDialogFragment() {

    @Inject lateinit var alarmSoundServiceHelper: AlarmSoundServiceHelper
    @Inject lateinit var aapsLogger: AAPSLogger

    var helperActivity: ErrorHelperActivity? = null
    var status: String = ""
    var title: String = ""
    var sound: Int = 0

    private var loopHandler = Handler()

    private var _binding: DialogErrorBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
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
        _binding = DialogErrorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = title
        binding.ok.setOnClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog ok button pressed")
            dismiss()
        }
        binding.mute.setOnClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog mute button pressed")
            stopAlarm()
        }
        binding.mute5min.setOnClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog mute 5 min button pressed")
            stopAlarm()
            loopHandler.postDelayed(this::startAlarm, T.mins(5).msecs())
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
        binding.status.text = status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        helperActivity?.finish()
        loopHandler.removeCallbacksAndMessages(null)
        stopAlarm()
    }

    private fun startAlarm() {
        if (sound != 0)
            context?.let { context -> alarmSoundServiceHelper.startAlarm(context, sound) }
    }

    private fun stopAlarm() =
        context?.let { context -> alarmSoundServiceHelper.stopService(context) }
}
