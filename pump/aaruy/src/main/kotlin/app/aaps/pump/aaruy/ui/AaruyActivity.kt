package app.aaps.pump.aaruy.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.KeyEvent
import android.view.MenuItem
import android.view.WindowManager
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.extensions.safeGetSerializableExtra
import app.aaps.pump.aaruy.R
import app.aaps.pump.aaruy.code.ConnectionState
import app.aaps.pump.aaruy.code.ReplaceStep
import app.aaps.pump.aaruy.common.enums.AaruyPumpState
import app.aaps.pump.aaruy.common.enums.AlarmState
import app.aaps.pump.aaruy.common.util.AaruyConst
import app.aaps.pump.aaruy.databinding.ActivityAaruyBinding
import app.aaps.pump.aaruy.extension.replaceFragmentInActivity
import app.aaps.pump.aaruy.ui.dialog.AaruyAlertDialog
import app.aaps.pump.aaruy.ui.dialog.YUtils
import app.aaps.pump.aaruy.ui.viewmodel.AaruyViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AaruyActivity : AaruyBaseActivity<ActivityAaruyBinding>() {

    private var alertDialog: AaruyAlertDialog? = null
    private var isBacking = false
    private var reservoirFirstFragment: AaruyReservoirBatFragment? = null
    private var isRemoveAir: Boolean = false
    private val handlerConnect = Handler(Looper.getMainLooper())

    override fun getLayoutId(): Int = R.layout.activity_aaruy

    @SuppressLint("HandlerLeak")
    private inner class MyHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            if (msg.what == 1) {
                myHandler!!.sendEmptyMessageDelayed(1, 100)
            } else if (msg.what == 10) {
                myHandler!!.postDelayed(Runnable {
                    if (YUtils.loadingIsShowing()) {
                        if (isBacking) {
                            isBacking = false
                            lifecycleScope.launch {
                                YUtils.hideLoading()
                            }
                        }
                    } else {
                        aapsLogger.error(LTag.PUMP, "1 min is up")
                    }
                }, 60000)
            } else if (msg.what == 5) {
                myHandler!!.postDelayed(Runnable {
                    if (YUtils.loadingIsShowing()) {
                        lifecycleScope.launch {
                            YUtils.hideLoading()
                        }
                    } else {
                        aapsLogger.error(LTag.PUMP, "0.5 sec is up")
                    }
                }, 500)
            }
        }
    }

    private var myHandler: MyHandler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activity = this
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        title = getString(R.string.replace_bat_rescv_label)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        // 蓝牙定时检测重连
        val runnable = object : Runnable {
            override fun run() {
                binding.viewModel?.apply {
                    if (aaruyPump.connectionState == ConnectionState.DISCONNECTED) {
                        connectDevice()
                    }
                }
                handlerConnect.postDelayed(this, 3000)
            }
        }
        handlerConnect.postDelayed(runnable, 3000)

        binding.apply {
            viewModel = ViewModelProvider(this@AaruyActivity, viewModelFactory)[AaruyViewModel::class.java]
            viewModel?.apply {
                processIntent(intent)

                replaceStep.observe(this@AaruyActivity) {
                    when (it) {
                        ReplaceStep.PUSH_TEST            -> {
                            // setupViewFragment(AaruyTestFragment.newInstance())
                        }
                        ReplaceStep.REPLACE_STEP_1      -> {
                            reservoirFirstFragment = AaruyReservoirBatFragment()
                            setupViewFragment(reservoirFirstFragment!!)
                            reservoirFirstFragment!!.notifyTextView(it)

                            if (alertDialog == null)
                                alertDialog = AaruyAlertDialog(
                                    activity,
                                    getString(R.string.replace_bat_rescv_label),
                                    getString(R.string.actions_reservoir_rewind_prompt),
                                    true,
                                    1
                                ) { _, isPositive ->
                                    if (isPositive) {
                                        if (aaruyPump.connectionState == ConnectionState.CONNECTED) {
                                            rewindPump()
                                        } else {
                                            connectDevice()
                                            // OKDialog.show(this@AaruyActivity, "", rh.gs(R.string.bluetooth_disconnect)) { this@AaruyActivity.finish() }
                                            ToastUtils.errorToast(context, context.getString(R.string.bluetooth_disconnect))
                                            this@AaruyActivity.finish()
                                        }

                                    } else {
                                        this@AaruyActivity.finish()
                                    }
                                }
                            if (!alertDialog!!.isShowing) {
                                alertDialog!!.show()
                            }
                        }
                        ReplaceStep.REPLACE_STEP_2      -> {
                            if (reservoirFirstFragment == null) {
                                reservoirFirstFragment = AaruyReservoirBatFragment()
                                setupViewFragment(reservoirFirstFragment!!)
                            }
                            reservoirFirstFragment!!.notifyTextView(it)
                        }
                        ReplaceStep.REPLACE_STEP_3      -> {
                            if (reservoirFirstFragment == null) {
                                reservoirFirstFragment = AaruyReservoirBatFragment()
                                setupViewFragment(reservoirFirstFragment!!)
                            }
                            reservoirFirstFragment!!.notifyTextView(it)
                        }
                        ReplaceStep.REPLACE_STEP_4      -> {
                            if (reservoirFirstFragment == null) {
                                reservoirFirstFragment = AaruyReservoirBatFragment()
                                setupViewFragment(reservoirFirstFragment!!)
                            }
                            reservoirFirstFragment!!.notifyTextView(it)
                        }
                        null                            -> Unit
                    }
                }

                currentStatus.observe(this@AaruyActivity) {
                    when(it) {
                        AaruyPumpState.NO_RUNNING           -> {

                        }
                        AaruyPumpState.BASALRATE_RUNNING    -> {
                            if (replaceStep.value == ReplaceStep.REPLACE_STEP_4) {
                                this@AaruyActivity.finish()
                            }
                        }
                        AaruyPumpState.TMPBASALRATE_RUNNING -> {

                        }
                        AaruyPumpState.LARGEDOSE_RUNNING    -> {

                        }
                        AaruyPumpState.EXHAUSTING           -> {
                            YUtils.showLoading(activity as AaruyActivity, getString(R.string.is_exhaust))
                            isRemoveAir = true
                        }
                        AaruyPumpState.PUSHBACK_RUNNING     -> {
                            isBacking = true
                            if (!YUtils.loadingIsShowing()) {
                                YUtils.showLoading(activity as AaruyActivity, getString(R.string.is_backing))
                            }
                        }
                        AaruyPumpState.PUSHFORWARD_RUNNING  -> {

                        }
                        AaruyPumpState.PAUSE_RUNNING        -> {

                        }
                        AaruyPumpState.STOP_RUNNING         -> {
                            if (isRemoveAir) {
                                startBaseInject()
                                aaruyPump.alreadyPushBack = false
                                resetAlarms()
                            }
                            if (isBacking) {
                                isBacking = false
                                if (YUtils.loadingIsShowing()) {
                                    YUtils.hideLoading()
                                }
                            }
                        }
                        AaruyPumpState.FIRST_INIT           -> {

                        }
                        null                             -> Unit
                    }
                }

                aaruyPump.errCode.observe(this@AaruyActivity) {
                    if ((it shr 8) == 0) {
                        when ((it and 0xff).toByte()) {
                            AaruyConst.errCodeType.FLAG_PUSH_BACK_STATUS            -> {
                                isBacking = false
                                lifecycleScope.launch {
                                    if (YUtils.loadingIsShowing()) {
                                        YUtils.hideLoading()
                                    }
                                    delay(3000)
                                    if (YUtils.loadingIsShowing()) {
                                        YUtils.hideLoading()
                                    }
                                }
                            }

                            AaruyConst.errCodeType.DISCHARGE_LIQUID                 -> {
                                YUtils.hideLoading()
                            }

                            AaruyConst.errCodeType.FLAG_LOCATE_POS_LIQUID_DISCHARGE -> {
                                YUtils.hideLoading()
                                reservoirFirstFragment?.isPushLocating = false
                                reservoirFirstFragment?.setNextBtnEnable(true)
                                showSuccessDialog(rh.gs(R.string.locate_check_liquid))
                            }

                            AaruyConst.errCodeType.FLAG_LOCATE_POSITION             -> {
                                YUtils.hideLoading()
                                reservoirFirstFragment?.setNextBtnEnable(true)
                                if (reservoirFirstFragment?.isPushLocating == true) {
                                    startPush()
                                }
                            }
                        }
                    }
                    else if ((it shr 8) == 0x81) {
                        if(AlarmState.isHighAlarm(it)) {
                            this@AaruyActivity.finish()
                        }
                    }
                }

            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (myHandler != null) {
            myHandler!!.removeCallbacksAndMessages(null)
            myHandler = null
        }
        if (alertDialog != null) {
            if (alertDialog!!.isShowing) {
                alertDialog!!.dismiss()
            }
            alertDialog = null
        }

        handlerConnect.removeCallbacksAndMessages(null)
        YUtils.hideLoading()
    }

    // override fun onNewIntent(intent: Intent?) {
    //     super.onNewIntent(intent)
    //     processIntent(intent)
    // }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home->{
                if (isBacking) {
                    ToastUtils.errorToast(context, context.getString(R.string.is_backing))
                    if (!YUtils.loadingIsShowing()) {
                        YUtils.showLoading(activity as AaruyActivity, getString(R.string.is_backing))
                    }
                    return true
                }
                showPushDialog()

                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (isBacking) {
            ToastUtils.errorToast(context, context.getString(R.string.is_backing))
            if (!YUtils.loadingIsShowing()) {
                YUtils.showLoading(activity as AaruyActivity, getString(R.string.is_backing))
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showPushDialog()
        }
        return true
    }

    private fun processIntent(intent: Intent?) {
        binding.viewModel?.apply {
            intent?.run {
                val step = intent.safeGetSerializableExtra(EXTRA_START_REPLACE_STEP, ReplaceStep::class.java)
                if (step != null) {
                    initializePatchStep(step)
                }
            }
        }
    }

    companion object {

        const val EXTRA_START_REPLACE_STEP = "EXTRA_START_REPLACE_FRAGMENT_UI"
        private const val EXTRA_START_FROM_MENU = "EXTRA_START_FROM_MENU"

        @JvmStatic fun createIntentFromMenu(context: Context, replaceStep: ReplaceStep): Intent {
            return Intent(context, AaruyActivity::class.java).apply {
                putExtra(EXTRA_START_REPLACE_STEP, replaceStep)
                putExtra(EXTRA_START_FROM_MENU, true)
            }
        }

    }

    private fun setupViewFragment(baseFragment: AaruyBaseFragment<*>) {
        replaceFragmentInActivity(baseFragment, R.id.framelayout_fragment, false)
    }

    fun startPush() {
        if (!YUtils.loadingIsShowing()) {
            YUtils.showLoading(activity as AaruyActivity, "")
        }
        binding.viewModel?.apply {
            pushForward()
        }

        if (myHandler == null) {
            myHandler = MyHandler()
        }
        myHandler!!.sendEmptyMessage(5)
    }


    private fun showSuccessDialog(tip: String) {
        val tipMessage = getString(R.string.notifyTitle)

        alertDialog = AaruyAlertDialog(
            activity,
            tipMessage,
            tip,
            false,
            1
        ) { _, _ ->
            alertDialog = null
        }
        try {
            if (activity != null && !activity!!.isFinishing) {
                alertDialog!!.show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showPushDialog() {
        alertDialog = AaruyAlertDialog(
            activity,
            getString(R.string.notifyTitle),
            getString(R.string.if_exit_replace_view),
            true,
            1
        ) { _, isPositive ->
            if (isPositive) {
                this@AaruyActivity.finish()
            }
        }
        alertDialog?.show()
    }

    private fun rewindPump() {
        binding.viewModel?.apply {
            replaceDevice()
        }

        if (myHandler == null) {
            myHandler = MyHandler()
        }
        myHandler!!.sendEmptyMessage(10)
    }

    fun startRemoveAir() {
        binding.viewModel?.apply {
            if (aaruyPump.connectionState != ConnectionState.CONNECTED) {
                ToastUtils.errorToast(context, context.getString(R.string.bluetooth_disconnect))
                return
            }
            needlePushAir()
        }
    }
}