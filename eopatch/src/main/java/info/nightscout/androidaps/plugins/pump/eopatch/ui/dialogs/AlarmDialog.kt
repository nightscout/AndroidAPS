package info.nightscout.androidaps.plugins.pump.eopatch.ui.dialogs

import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.*
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.core.R
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmProcess
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.AlarmCode
import info.nightscout.androidaps.plugins.pump.eopatch.alarm.IAlarmProcess
import info.nightscout.androidaps.plugins.pump.eopatch.bindingadapters.setOnSafeClickListener
import info.nightscout.androidaps.plugins.pump.eopatch.ble.IPatchManager
import info.nightscout.androidaps.plugins.pump.eopatch.databinding.DialogAlarmBinding
import info.nightscout.androidaps.plugins.pump.eopatch.ui.AlarmHelperActivity
import info.nightscout.androidaps.services.AlarmSoundServiceHelper
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class AlarmDialog : DaggerDialogFragment() {

    @Inject lateinit var alarmSoundServiceHelper: AlarmSoundServiceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var patchManager: IPatchManager
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        mAlarmProcess = AlarmProcess(patchManager, rxBus)

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
                    .subscribeOn(Schedulers.io())
                    .subscribe ({ ret ->
                        aapsLogger.debug("Alarm processing result :${ret}")
                        if (ret == IAlarmProcess.ALARM_HANDLED) {
                            alarmCode?.let{
                                patchManager.preferenceManager.getAlarms().handle(it)
                                patchManager.preferenceManager.flushAlarms()
                            }
                            dismiss()
                        }else if (ret == IAlarmProcess.ALARM_PAUSE) {
                            isHolding = true
                        }else if (ret == IAlarmProcess.ALARM_UNHANDLED) {
                            if(!isMute){
                                startAlarm()
                            }
                        }
                    }, { t -> aapsLogger.error("${t.printStackTrace()}") })
            }
            stopAlarm()
        }
        binding.mute.setOnSafeClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog mute button pressed")
            isMute = true
            stopAlarm()
        }
        binding.mute5min.setOnSafeClickListener {
            aapsLogger.debug("USER ENTRY: Error dialog mute 5 min button pressed")
            stopAlarm()
            isMute = true
            handler.postDelayed(this::startAlarm, T.mins(5).msecs())
        }
        startAlarm()

        disposable = patchManager.observePatchLifeCycle()
            .observeOn(aapsSchedulers.main)
            .subscribe {
                if(it.isShutdown) {
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
        if(isHolding && !isMute){
            startAlarm()
        }
        binding.status.text = status
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        disposable?.dispose()
        disposable = null
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        handler.removeCallbacksAndMessages(null)
        helperActivity?.finish()
    }

    private fun startAlarm() {
        if (sound != 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context?.let { context -> alarmSoundServiceHelper.startAlarm(context, sound) }
            }
        }
    }

    private fun stopAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.let { context -> alarmSoundServiceHelper.stopService(context) }
        }
    }
}
