package app.aaps.pump.equil

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.BlePreCheck
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.UIRunnable
import app.aaps.core.ui.extensions.runOnUiThread
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.pump.equil.data.RunMode
import app.aaps.pump.equil.databinding.EquilFraBinding
import app.aaps.pump.equil.events.EventEquilDataChanged
import app.aaps.pump.equil.events.EventEquilModeChanged
import app.aaps.pump.equil.manager.EquilManager
import app.aaps.pump.equil.manager.command.CmdModelSet
import app.aaps.pump.equil.ui.EquilHistoryRecordActivity
import app.aaps.pump.equil.ui.EquilUnPairDetachActivity
import app.aaps.pump.equil.ui.dlg.LoadingDlg
import app.aaps.pump.equil.ui.pair.EquilPairActivity
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.time.Duration
import javax.inject.Inject

class EquilFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var blePreCheck: BlePreCheck
    @Inject lateinit var equilPumpPlugin: EquilPumpPlugin
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var equilManager: EquilManager

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var refreshLoop: Runnable

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

        updateGUI()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    @SuppressLint("SetTextI18n")
    @Synchronized
    fun updateGUI() {
        if (_binding == null) return
        val devName = equilManager.equilState?.serialNumber
        if (!TextUtils.isEmpty(devName)) {
            binding.battery.text = equilManager.equilState?.battery.toString() + "%"
            binding.insulinReservoir.text = equilManager.equilState?.currentInsulin.toString()
            binding.basalSpeed.text = String.format(rh.gs(R.string.equil_unit_u_hours), equilPumpPlugin.baseBasalRate)
            binding.firmwareVersion.text = equilManager.equilState?.firmwareVersion
            equilManager.equilState?.startInsulin?.let {
                if (it == -1) {
                    binding.totalDelivered.text = "-"
                } else {
                    val totalDelivered = it - (equilManager.equilState?.currentInsulin ?: 0)
                    binding.totalDelivered.text = rh.gs(R.string.equil_unit_u, totalDelivered.toString())
                }
            }

            binding.timeDevice.text = dateUtil.dateAndTimeAndSecondsString(equilManager.equilState?.lastDataTime ?: 0L)
            val runMode = equilManager.equilState?.runMode
            binding.mode.setTextColor(rh.gac(app.aaps.core.ui.R.attr.defaultTextColor))
            if (equilManager.isActivationCompleted()) {
                when (runMode) {
                    RunMode.RUN     -> {
                        binding.mode.text = rh.gs(R.string.equil_mode_running)
                        binding.btnResumeDelivery.visibility = View.GONE
                        binding.btnSuspendDelivery.visibility = View.VISIBLE
                    }

                    RunMode.STOP    -> {
                        binding.mode.text = rh.gs(R.string.equil_mode_stopped)
                        binding.btnResumeDelivery.visibility = View.GONE
                        binding.btnSuspendDelivery.visibility = View.GONE
                    }

                    RunMode.SUSPEND -> {
                        binding.mode.text = rh.gs(R.string.equil_mode_suspended)
                        binding.btnResumeDelivery.visibility = View.VISIBLE
                        binding.btnSuspendDelivery.visibility = View.GONE
                    }

                    else            -> {
                        binding.btnResumeDelivery.visibility = View.VISIBLE
                        binding.btnSuspendDelivery.visibility = View.GONE
                        binding.mode.text = ""
                    }
                }
            } else {
                binding.btnResumeDelivery.visibility = View.GONE
                binding.btnSuspendDelivery.visibility = View.GONE
                binding.mode.text = rh.gs(R.string.equil_init_insulin_error)
                binding.mode.setTextColor(rh.gac(app.aaps.core.ui.R.attr.warningColor))
            }

            binding.serialNumber.text = devName
            updateTempBasal()
            equilManager.equilState?.bolusRecord.let {
                binding.lastBolus.text =
                    if (it == null) "-"
                    else
                        rh.gs(R.string.equil_common_overview_last_bolus_value, it.amount, rh.gs(app.aaps.core.ui.R.string.insulin_unit_shortname), readableDuration(Duration.ofMillis(System.currentTimeMillis() - it.startTime)))
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
            binding.timeDevice.text = '-'.toString()
            binding.mode.text = '-'.toString()
            binding.serialNumber.text = "-"
            binding.firmwareVersion.text = "-"
            binding.insulinReservoir.text = "-"
            binding.totalDelivered.text = "-"
            binding.btnBind.visibility = View.VISIBLE
            binding.btnOpDressing.visibility = View.GONE
            binding.btnUnbind.visibility = View.GONE
            binding.btnHistory.visibility = View.GONE
            binding.btnResumeDelivery.visibility = View.GONE
            binding.btnSuspendDelivery.visibility = View.GONE
            binding.btnBind.setOnClickListener {
                if (!blePreCheck.prerequisitesCheck(activity as AppCompatActivity)) {
                    ToastUtils.errorToast(activity, getString(app.aaps.core.ui.R.string.need_connect_permission))
                } else {
                    startActivity(Intent(context, EquilPairActivity::class.java).apply { putExtra(EquilPairActivity.KEY_TYPE, EquilPairActivity.Type.PAIR) })
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
                changeInsulin()

        }
    }

    private fun showSetModeDialog() {
        val runMode = equilManager.equilState?.runMode
        var tempMode = RunMode.RUN
        if (runMode == RunMode.RUN) {
            tempMode = RunMode.SUSPEND
        }
        showLoading()
        commandQueue.customCommand(CmdModelSet(tempMode.command, aapsLogger, preferences, equilManager), object : Callback() {
            override fun run() {
                dismissLoading()
                aapsLogger.debug(LTag.PUMPCOMM, "result====" + result.success)
                if (result.success) {
                    equilManager.equilState?.runMode = tempMode
                    runOnUiThread { updateGUI() }
                }
            }
        })

    }

    private fun updateTempBasal() {
        val tempBasal = equilManager.equilState?.tempBasal
        if (tempBasal != null && equilManager.isTempBasalRunning()) {
            val startTime = tempBasal.startTime
            val duration = tempBasal.duration / 60 / 1000
            val minutesRunning = Duration.ofMillis(System.currentTimeMillis() - startTime).toMinutes()
            binding.tempBasal.text = rh.gs(R.string.equil_common_overview_temp_basal_value, tempBasal.rate, dateUtil.timeString(startTime), minutesRunning, duration)
        } else {
            binding.tempBasal.text = "-"
        }
    }

    fun updateModel() {
        // binding.mode.text = equilPumpPlugin.equilManager.equilServiceData.mode.toString()
    }

    private fun showLoading() {
        LoadingDlg().show(childFragmentManager, "loading")
    }

    private fun dismissLoading() {
        try {
            if (isAdded) {
                val fragment = childFragmentManager.findFragmentByTag("loading")
                if (fragment is DialogFragment) {
                    fragment.dismiss()
                }
            }
        } catch (e: IllegalStateException) {
            // dialog not running yet
            aapsLogger.error("Unhandled exception", e)
        }
    }

    @Synchronized
    fun changeInsulin() {

        activity?.let { activity ->
            context?.let { context ->
                protectionCheck.queryProtection(
                    activity, ProtectionCheck.Protection.PREFERENCES,
                    UIRunnable {
                        val intent = Intent(context, EquilPairActivity::class.java)
                        intent.putExtra(EquilPairActivity.KEY_TYPE, EquilPairActivity.Type.CHANGE_INSULIN)
                        startActivity(intent)
                    }
                )
            }
        }
    }

    private fun readableDuration(duration: Duration): String {
        val hours = duration.toHours().toInt()
        val minutes = duration.toMinutes().toInt()
        val seconds = duration.seconds
        when {
            seconds < 10           -> return rh.gs(R.string.equil_common_moments_ago)

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
