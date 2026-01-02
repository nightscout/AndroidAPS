package app.aaps.ui.alertDialogs

import android.content.res.Resources
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.ui.databinding.DialogErrorBinding
import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject

class ErrorDialog : DaggerDialogFragment() {

    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger

    var helperActivity: TranslatedDaggerAppCompatActivity? = null
    var status: String = ""
    var title: String = ""
    var sound: Int = 0

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var _binding: DialogErrorBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val theme: Resources.Theme? = context?.theme
        theme?.applyStyle(app.aaps.core.ui.R.style.AppTheme_NoActionBar, true)

        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = true
        dialog?.setCanceledOnTouchOutside(false)

        savedInstanceState?.let { bundle ->
            bundle.getString("status")?.let { status = it }
            bundle.getString("title")?.let { title = it }
            sound = bundle.getInt("sound", app.aaps.core.ui.R.raw.error)
        }
        aapsLogger.debug("Error dialog displayed")
        _binding = DialogErrorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = title
        binding.ok.setOnClickListener {
            uel.log(Action.ERROR_DIALOG_OK, Sources.Unknown)
            stopAlarm("Dismiss")
            dismiss()
        }
        binding.mute.setOnClickListener {
            uel.log(Action.ERROR_DIALOG_MUTE, Sources.Unknown)
            stopAlarm("Mute")
        }
        binding.mute5min.setOnClickListener {
            uel.log(Action.ERROR_DIALOG_MUTE_5MIN, Sources.Unknown)
            stopAlarm("Mute 5 min")
            handler.postDelayed(this::startAlarm, T.mins(5).msecs())
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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        helperActivity?.finish()
    }

    private fun startAlarm() {
        if (sound != 0)
            uiInteraction.startAlarm(sound, "$title:$status")
    }

    private fun stopAlarm(reason: String) =
        uiInteraction.stopAlarm(reason)
}
