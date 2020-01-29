package info.nightscout.androidaps.dialogs

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.BolusProgressHelperActivity
import info.nightscout.androidaps.events.EventPumpStatusChanged
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus.toObservable
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissBolusProgressIfRunning
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_bolusprogress.*
import org.slf4j.LoggerFactory

class BolusProgressDialog : DialogFragment() {
    private val log = LoggerFactory.getLogger(L.UI)
    private val disposable = CompositeDisposable()

    companion object {
        private val DEFAULT_STATE = MainApp.gs(R.string.waitingforpump)
        @JvmField
        var bolusEnded = false
        @JvmField
        var stopPressed = false
    }

    private var running = true
    private var amount = 0.0
    private var state: String? = null
    private var helpActivity: BolusProgressHelperActivity? = null

    fun setInsulin(amount: Double): BolusProgressDialog {
        this.amount = amount
        bolusEnded = false
        return this
    }

    fun setHelperActivity(activity: BolusProgressHelperActivity): BolusProgressDialog {
        helpActivity = activity
        return this
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
        return inflater.inflate(R.layout.dialog_bolusprogress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        overview_bolusprogress_title.text = String.format(MainApp.gs(R.string.overview_bolusprogress_goingtodeliver), amount)
        overview_bolusprogress_stop.setOnClickListener {
            if (L.isEnabled(L.UI)) log.debug("Stop bolus delivery button pressed")
            stopPressed = true
            overview_bolusprogress_stoppressed.visibility = View.VISIBLE
            overview_bolusprogress_stop.visibility = View.INVISIBLE
            ConfigBuilderPlugin.getPlugin().commandQueue.cancelAllBoluses()
        }
        overview_bolusprogress_progressbar.max = 100
        state = savedInstanceState?.getString("state", DEFAULT_STATE) ?: DEFAULT_STATE
        overview_bolusprogress_status.text = state
        stopPressed = false
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        if (L.isEnabled(L.UI)) log.debug("onResume")
        if (!ConfigBuilderPlugin.getPlugin().commandQueue.bolusInQueue())
            bolusEnded = true

        if (bolusEnded) dismiss()
        else running = true

        disposable.add(toObservable(EventPumpStatusChanged::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ overview_bolusprogress_status.text = it.getStatus() }) { FabricPrivacy.logException(it) }
        )
        disposable.add(toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ if (running) dismiss() }) { FabricPrivacy.logException(it) }
        )
        disposable.add(toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                if (L.isEnabled(L.UI)) log.debug("Status: " + it.status + " Percent: " + it.percent)
                overview_bolusprogress_status.text = it.status
                overview_bolusprogress_progressbar.progress = it.percent
                if (it.percent == 100) {
                    overview_bolusprogress_stop.visibility = View.INVISIBLE
                    scheduleDismiss()
                }
                state = it.status
            }) { FabricPrivacy.logException(it) }
        )
    }

    override fun dismiss() {
        if (L.isEnabled(L.UI)) log.debug("dismiss")
        try {
            super.dismiss()
        } catch (e: IllegalStateException) {
            // dialog not running yet. onResume will try again. Set bolusEnded to make extra
            // sure onResume will catch this
            bolusEnded = true
            log.error("Unhandled exception", e)
        }
        helpActivity?.finish()
    }

    override fun onPause() {
        super.onPause()
        if (L.isEnabled(L.UI)) log.debug("onPause")
        running = false
        disposable.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("state", state)
    }

    private fun scheduleDismiss() {
        if (L.isEnabled(L.UI)) log.debug("scheduleDismiss")
        Thread(Runnable {
            SystemClock.sleep(5000)
            bolusEnded = true
            val activity: Activity? = activity
            activity?.runOnUiThread {
                if (running) {
                    if (L.isEnabled(L.UI)) log.debug("executing")
                    try {
                        dismiss()
                    } catch (e: Exception) {
                        log.error("Unhandled exception", e)
                    }
                }
            }
        }).start()
    }
}
