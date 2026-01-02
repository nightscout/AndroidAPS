package app.aaps.pump.eopatch.ui.dialogs

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.ui.R
import app.aaps.pump.eopatch.alarm.AlarmCode
import app.aaps.pump.eopatch.alarm.AlarmProcess
import app.aaps.pump.eopatch.alarm.IAlarmProcess
import app.aaps.pump.eopatch.bindingadapters.setOnSafeClickListener
import app.aaps.pump.eopatch.ble.IPatchManager
import app.aaps.pump.eopatch.ble.PatchManagerExecutor
import app.aaps.pump.eopatch.ble.PreferenceManager
import app.aaps.pump.eopatch.databinding.DialogAlarmBinding
import app.aaps.pump.eopatch.ui.AlarmHelperActivity
import app.aaps.pump.eopatch.vo.Alarms
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.Disposable
import javax.inject.Inject

class AlarmDialog : DaggerDialogFragment() {

    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var patchManager: IPatchManager
    @Inject lateinit var patchManagerExecutor: PatchManagerExecutor
    @Inject lateinit var preferenceManager: PreferenceManager
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var pm: PreferenceManager
    @Inject lateinit var alarms: Alarms

    var helperActivity: AlarmHelperActivity? = null
    var alarmCode: AlarmCode? = null
    var code: String? = null
    var status: String = ""
    var title: String = ""
    var sound: Int = 0

    private lateinit var mAlarmProcess: IAlarmProcess
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var _binding: DialogAlarmBinding? = null
    private var disposable: Disposable? = null
    private val binding get() = _binding!!

    private var isHolding = false
    private var isMute = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        mAlarmProcess = AlarmProcess(patchManager, patchManagerExecutor, rxBus)

        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)

        savedInstanceState?.let { bundle ->
            bundle.getString("status")?.let { status = it }
            bundle.getString("title")?.let { title = it }
            bundle.getString("code")?.let {
                code = it
                alarmCode = AlarmCode.fromStringToCode(it)
            }
            sound = bundle.getInt("sound", R.raw.error)
        }
        aapsLogger.debug("Alarm dialog displayed")
        _binding = DialogAlarmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.title.text = title
        binding.ok.setOnSafeClickListener {
            aapsLogger.debug("USER ENTRY: Alarm dialog ok button pressed")
            alarmCode?.let { ac ->
                mAlarmProcess.doAction(requireContext(), ac)
                    .subscribeOn(aapsSchedulers.io)
                    .subscribe({ ret ->
                                   aapsLogger.debug("Alarm processing result :$ret")
                                   if (ret == IAlarmProcess.ALARM_HANDLED || ret == IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP) {
                                       if (ret == IAlarmProcess.ALARM_HANDLED_BUT_NEED_STOP_BEEP) {
                                           alarms.needToStopBeep.add(ac)
                                       }
                                       alarmCode?.let {
                                           alarms.handle(it)
                                           preferenceManager.flushAlarms()
                                       }
                                       dismiss()
                                   } else if (ret == IAlarmProcess.ALARM_PAUSE) {
                                       isHolding = true
                                   } else if (ret == IAlarmProcess.ALARM_UNHANDLED) {
                                       if (!isMute) {
                                           startAlarm("ALARM_UNHANDLED")
                                       }
                                   }
                               }, { t -> aapsLogger.error("${t.printStackTrace()}") })
            }
            stopAlarm("OK clicked")
        }
        binding.mute.setOnSafeClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog mute button pressed")
            isMute = true
            stopAlarm("Mute clicked")
        }
        binding.mute5min.setOnSafeClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog mute 5 min button pressed")
            stopAlarm("Mute5m clicked")
            isMute = true
            handler.postDelayed({ startAlarm("post") }, T.mins(5).msecs())
        }
        startAlarm("onViewCreated")

        disposable = preferenceManager.observePatchLifeCycle()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                if (it.isShutdown) {
                    activity?.finish()
                }
            }

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
        if (isHolding && !isMute) {
            startAlarm("onResume")
        }
        binding.status.text = status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        disposable?.dispose()
        disposable = null
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

    private fun startAlarm(reason: String) {
        if (sound != 0) uiInteraction.startAlarm(sound, reason)
    }

    private fun stopAlarm(reason: String) {
        uiInteraction.stopAlarm(reason)
    }
}
