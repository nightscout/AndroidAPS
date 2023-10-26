package app.aaps.pump.equil

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import app.aaps.core.interfaces.extensions.runOnUiThread
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.T
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.data.AlarmMode
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.databinding.EquilFraBinding
import app.aaps.pump.equil.manager.command.*
import app.aaps.pump.equil.ui.*
import app.aaps.pump.equil.ui.dlg.LoadingDlg
import app.aaps.pump.equil.ui.dlg.SingleChooseDlg
import app.aaps.pump.equil.ui.pair.EquilPairActivity
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.time.Duration
import javax.inject.Inject
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.pump.WarnColors
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.events.EventEquilModeChanged

class EquilFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var sp: SP
    @Inject lateinit var warnColors: WarnColors
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uel: UserEntryLogger

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var refreshLoop: Runnable

    private var _binding: EquilFraBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    init {
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGUI() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = EquilFraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        handler.postDelayed(refreshLoop, T.mins(1).msecs())

        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventEquilDataChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventEquilModeChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateModel() }, fabricPrivacy::logException)
        // disposable += rxBus
        //     .toObservable(EventEquilInsulinChanged::class.java)
        //     .observeOn(aapsSchedulers.main)
        //     .subscribe({ changeInsulinSuccess() }, fabricPrivacy::logException)

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacks(refreshLoop)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        var devName = equilPumpPlugin.equilManager.serialNumber
        if (!TextUtils.isEmpty(devName)) {
            binding.battery.text = equilPumpPlugin.equilManager.battery.toString() + "%"
            binding.insulinReservoir.text = equilPumpPlugin.equilManager.currentInsulin.toString()
            binding.basalSpeed.text = String.format(rh.gs(R.string.equil_unit_u_hours), equilPumpPlugin.baseBasalRate)
            binding.firmwareVersion.text = equilPumpPlugin.equilManager.firmwareVersion?.toString()
            equilPumpPlugin.equilManager.startInsulin.let {
                if (it == -1) {
                    binding.totalDelivered.text = "-"
                } else {
                    var totalDelivered = it - equilPumpPlugin.equilManager.currentInsulin;
                    binding.totalDelivered.text = String.format(
                        rh.gs(R.string.equil_unit_u),
                        totalDelivered.toString()
                    )
                }
            }

            binding.timeLastGetdata.text = android.text.format.DateFormat.format(
                "yyyy-MM-dd HH:mm:ss",
                equilPumpPlugin.equilManager.lastDataTime
            ).toString()
            binding.timeDevice.text = android.text.format.DateFormat.format(
                "yyyy-MM-dd HH:mm:ss",
                equilPumpPlugin.equilManager.lastDataTime
            ).toString()
            var runMode = equilPumpPlugin.equilManager.runMode;
            binding.mode.setTextColor(Color.WHITE)
            if (equilPumpPlugin.equilManager.isActivationCompleted) {
                if (runMode == RunMode.RUN) {
                    binding.mode.text = rh.gs(R.string.equil_mode_deliver)
                    binding.btnResumeDelivery.visibility = View.GONE
                    binding.btnSuspendDelivery.visibility = View.VISIBLE
                } else if (runMode == RunMode.STOP) {
                    binding.mode.text = rh.gs(R.string.equil_mode_stop)
                    binding.btnResumeDelivery.visibility = View.GONE
                    binding.btnSuspendDelivery.visibility = View.GONE
                } else if (runMode == RunMode.SUSPEND) {
                    binding.mode.text = rh.gs(R.string.equil_mode_suspend)
                    binding.btnResumeDelivery.visibility = View.VISIBLE
                    binding.btnSuspendDelivery.visibility = View.GONE
                } else {
                    binding.btnResumeDelivery.visibility = View.VISIBLE
                    binding.btnSuspendDelivery.visibility = View.GONE
                    binding.mode.text = ""
                }
            } else {
                binding.btnResumeDelivery.visibility = View.GONE
                binding.btnSuspendDelivery.visibility = View.GONE
                binding.mode.text = rh.gs(R.string.equil_init_insulin_error)
                binding.mode.setTextColor(Color.RED)
            }
            binding.serialNumber.text = devName
            updateTempBasal()
            updateAlarm()
            runOnUiThread {
                equilPumpPlugin.equilManager.bolusRecord.let {
                    if (it == null) {
                        binding.lastBolus.text = "-"
                    } else {
                        val text = rh.gs(
                            R.string.equil_common_overview_last_bolus_value,
                            it?.amout,
                            rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname),
                            readableDuration(Duration.ofMillis(System.currentTimeMillis() - it.startTime))
                        )
                        binding.lastBolus.text = text
                    }

                }
            }
            binding.tvTone.setOnClickListener {
                showSingleChooseDlg()
            }

            binding.btnBind.visibility = View.GONE
            binding.btnOpDressing.visibility = View.VISIBLE
            binding.btnUnbind.visibility = View.VISIBLE
            binding.btnHistory.visibility = View.VISIBLE

        } else {
            binding.tempBasal.text = "-"
            binding.lastBolus.text = '-'.toString()
            binding.battery.text = '-'.toString()
            binding.basalSpeed.text = '-'.toString()
            binding.timeLastGetdata.text = '-'.toString()
            binding.timeDevice.text = '-'.toString()
            binding.mode.text = '-'.toString()
            binding.serialNumber.text = "-".toString()
            binding.firmwareVersion.text = "-"
            binding.insulinReservoir.text = "-"
            binding.tvTone.text = "-"
            binding.totalDelivered.text = "-"
            binding.tvTone.setOnClickListener {
            }
            binding.btnBind.visibility = View.VISIBLE
            binding.btnOpDressing.visibility = View.GONE
            binding.btnUnbind.visibility = View.GONE
            binding.btnHistory.visibility = View.GONE
            binding.btnResumeDelivery.visibility = View.GONE
            binding.btnSuspendDelivery.visibility = View.GONE
            binding.btnBind.setOnClickListener {
                if (equilPumpPlugin.checkProfile()) {
                    val intent = Intent(context, EquilPairActivity::class.java)
                    intent.putExtra(EquilPairActivity.KEY_TYPE, EquilPairActivity.Type.PAIR)
                    startActivity(intent)
                } else {
                    equilPumpPlugin.showToast(rh.gs(R.string.equil_noprofileset))
                }
            }
        }
        binding.imv.setOnClickListener {
            // commandQueue.customCommand(CmdStatusGet(), null)
        }
        binding.progressDeviceTime.visibility = View.GONE
        binding.btnHistory.setOnClickListener {
            if (TextUtils.isEmpty(devName)) {
                ToastUtils.errorToast(context, rh.gs(R.string.equil_error_no_devices))
            } else {
                startActivity(Intent(context, EquilHistoryRecordActivity::class.java))
            }
        }

        binding.btnResumeDelivery.setOnClickListener {
            showSetModeDialog()
        }
        binding.btnSuspendDelivery.setOnClickListener {
            showSetModeDialog()
        }
        binding.btnUnbind.setOnClickListener {
            if (TextUtils.isEmpty(devName)) {
                ToastUtils.errorToast(context, rh.gs(R.string.equil_error_no_devices))
            } else
                startActivity(Intent(context, EquilUnPairDetachActivity::class.java))

        }
        binding.btnOpDressing.setOnClickListener {
            if (TextUtils.isEmpty(devName)) {
                ToastUtils.errorToast(context, rh.gs(R.string.equil_error_no_devices))
            } else
                changeInsulin();

        }
    }

    private fun showSingleChooseDlg() {
        val dialogFragment = SingleChooseDlg(equilPumpPlugin.equilManager.alarmMode)
        dialogFragment.setDialogResultListener { data ->
            showLoading()
            commandQueue.customCommand(CmdAlarmSet(data.data.command), object : Callback() {
                override fun run() {
                    dismissLoading();
                    aapsLogger.debug(LTag.EQUILBLE, "result====" + result.success)
                    if (result.success) {
                        equilPumpPlugin.equilManager.alarmMode = data.data;
                        runOnUiThread {
                            updateGUI()
                        }
                    } else {

                    }
                }
            })

        }
        dialogFragment.show(childFragmentManager, "SingleChooseDlg")
    }

    private fun showSetModeDialog() {
        var runMode = equilPumpPlugin.equilManager.runMode;
        var tempMode = RunMode.RUN
        // var msg = "是否开始输注?"
        if (runMode == RunMode.RUN) {
            tempMode = RunMode.SUSPEND
            // msg = "是否暂停输注？"
        }
        showLoading()
        commandQueue.customCommand(CmdModelSet(tempMode.command), object : Callback() {
            override fun run() {
                dismissLoading();
                aapsLogger.debug(LTag.EQUILBLE, "result====" + result.success)
                if (result.success) {
                    equilPumpPlugin.equilManager.runMode = tempMode
                    runOnUiThread {
                        updateGUI()
                    }
                } else {
                }
            }
        })

    }

    private fun updateTempBasal() {
        val tempBasal = equilPumpPlugin.equilManager.tempBasal
        if (tempBasal != null && equilPumpPlugin.equilManager.isTempBasalRunning) {
            val startTime = tempBasal.startTime
            val rate = tempBasal.rate
            val duration = tempBasal.duration / 60 / 1000
            val minutesRunning = Duration.ofMillis(System.currentTimeMillis() - startTime).toMinutes()
            binding.tempBasal.text = rh.gs(
                R.string.equil_common_overview_temp_basal_value,
                rate,
                dateUtil.timeString(startTime),
                minutesRunning,
                duration
            )
        } else {
            binding.tempBasal.text = "-"
        }
    }

    fun updateAlarm() {
        var alarmMode = equilPumpPlugin.equilManager.alarmMode;
        if (alarmMode == AlarmMode.MUTE) {
            binding.tvTone.text = rh.gs(R.string.equil_tone_mode_mute)
        } else if (alarmMode == AlarmMode.TONE) {
            binding.tvTone.text = rh.gs(R.string.equil_tone_mode_tone)
        } else if (alarmMode == AlarmMode.SHAKE) {
            binding.tvTone.text = rh.gs(R.string.equil_tone_mode_shake)
        } else if (alarmMode == AlarmMode.TONE_AND_SHAKE) {
            binding.tvTone.text = rh.gs(R.string.equil_tone_mode_tone_and_shake)
        }
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    fun updateModel() {
        // binding.mode.text = equilPumpPlugin.equilManager.equilServiceData.mode.toString()
    }

    private fun showLoading() {
        LoadingDlg().also { dialog ->
        }.show(childFragmentManager, "loading")
    }

    private fun dismissLoading() {
        val fragment = childFragmentManager.findFragmentByTag("loading")
        if (fragment is DialogFragment) {
            fragment.dismiss()
        }
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    fun changeInsulin() {

        activity?.let { activity ->
            context?.let { context ->
                protectionCheck.queryProtection(
                    activity, ProtectionCheck.Protection.PREFERENCES,
                    UIRunnable {
                        if (equilPumpPlugin.checkProfile()) {
                            val intent = Intent(context, EquilPairActivity::class.java)
                            intent.putExtra(EquilPairActivity.KEY_TYPE, EquilPairActivity.Type.CHANGE_INSULIN)
                            startActivity(intent)
                        } else {
                            equilPumpPlugin.showToast(rh.gs(R.string.equil_noprofileset))
                        }

                    }
                )
            }
        }
    }

    fun getStatus() {
        equilPumpPlugin.equilManager.readStatus()
    }

    private fun readableDuration(duration: Duration): String {
        val hours = duration.toHours().toInt()
        val minutes = duration.toMinutes().toInt()
        val seconds = duration.seconds
        when {
            seconds < 10           -> {
                return rh.gs(R.string.equil_common_moments_ago)
            }

            seconds < 60           -> {
                return rh.gs(R.string.equil_common_less_than_a_minute_ago)
            }

            seconds < 60 * 60      -> { // < 1 hour
                return rh.gs(
                    R.string.equil_common_time_ago,
                    rh.gq(R.plurals.equil_common_minutes, minutes, minutes)
                )
            }

            seconds < 24 * 60 * 60 -> { // < 1 day
                val minutesLeft = minutes % 60
                if (minutesLeft > 0)
                    return rh.gs(
                        R.string.equil_common_time_ago,
                        rh.gs(
                            R.string.equil_common_composite_time,
                            rh.gq(R.plurals.equil_common_hours, hours, hours),
                            rh.gq(R.plurals.equil_common_minutes, minutesLeft, minutesLeft)
                        )
                    )
                return rh.gs(
                    R.string.equil_common_time_ago,
                    rh.gq(R.plurals.equil_common_hours, hours, hours)
                )
            }

            else                   -> {
                val days = hours / 24
                val hoursLeft = hours % 24
                if (hoursLeft > 0)
                    return rh.gs(
                        R.string.equil_common_time_ago,
                        rh.gs(
                            R.string.equil_common_composite_time,
                            rh.gq(R.plurals.equil_common_days, days, days),
                            rh.gq(R.plurals.equil_common_hours, hoursLeft, hoursLeft)
                        )
                    )
                return rh.gs(
                    R.string.equil_common_time_ago,
                    rh.gq(R.plurals.equil_common_days, days, days)
                )
            }
        }
    }

}
