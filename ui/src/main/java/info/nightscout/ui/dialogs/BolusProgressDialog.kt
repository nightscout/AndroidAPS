package info.nightscout.ui.dialogs

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissBolusProgressIfRunning
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.database.entities.UserEntry.Action
import app.aaps.database.entities.UserEntry.Sources
import dagger.android.support.DaggerDialogFragment
import info.nightscout.ui.databinding.DialogBolusprogressBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class BolusProgressDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private val disposable = CompositeDisposable()

    private var running = true
    private var amount = 0.0
    private var id: Long = 0L
    private var state: String? = null
    private var helpActivity: TranslatedDaggerAppCompatActivity? = null

    fun setId(id: Long): BolusProgressDialog {
        this.id = id
        return this
    }

    fun setInsulin(amount: Double): BolusProgressDialog {
        this.amount = amount
        BolusProgressData.bolusEnded = false
        return this
    }

    fun setHelperActivity(activity: TranslatedDaggerAppCompatActivity): BolusProgressDialog {
        helpActivity = activity
        return this
    }

    private var _binding: DialogBolusprogressBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
        context?.theme?.applyStyle(app.aaps.core.ui.R.style.AppTheme_NoActionBar, true)

        _binding = DialogBolusprogressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        savedInstanceState?.let {
            amount = it.getDouble("amount")
            id = it.getLong("id")
            state = it.getString("state") ?: rh.gs(app.aaps.core.ui.R.string.waitingforpump)
        }
        binding.title.text = rh.gs(app.aaps.core.ui.R.string.goingtodeliver, amount)
        binding.stop.setOnClickListener {
            aapsLogger.debug(LTag.UI, "Stop bolus delivery button pressed")
            BolusProgressData.stopPressed = true
            binding.stopPressed.visibility = View.VISIBLE
            binding.stop.visibility = View.INVISIBLE
            uel.log(Action.CANCEL_BOLUS, Sources.Overview, state)
            commandQueue.cancelAllBoluses(id)
        }
        binding.progressbar.max = 100
        binding.status.text = state
        BolusProgressData.stopPressed = false
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onResume() {
        super.onResume()
        aapsLogger.debug(LTag.UI, "onResume")
        if (!commandQueue.bolusInQueue())
            BolusProgressData.bolusEnded = true

        if (BolusProgressData.bolusEnded) dismiss()
        else running = true

        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe { binding.status.text = it.getStatus(requireContext()) }
        disposable += rxBus
            .toObservable(EventDismissBolusProgressIfRunning::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                aapsLogger.debug(LTag.PUMP, "Running id $id. Close request id  ${it.id}")
                if (it.id == null || it.id == id)
                    if (running) dismiss()
            }
        disposable += rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe {
                if (it.t?.id == id) {
                    aapsLogger.debug(LTag.UI, "Status: ${it.status} Percent: ${it.percent}")
                    binding.status.text = it.status
                    binding.progressbar.progress = it.percent
                    if (it.percent == 100) {
                        binding.stop.visibility = View.INVISIBLE
                        scheduleDismiss()
                    }
                    state = it.status
                }
            }
    }

    override fun dismiss() {
        aapsLogger.debug(LTag.UI, "dismiss")
        try {
            super.dismiss()
        } catch (e: IllegalStateException) {
            // dialog not running yet. onResume will try again. Set bolusEnded to make extra
            // sure onResume will catch this
            BolusProgressData.bolusEnded = true
            aapsLogger.error("Unhandled exception", e)
        }
        // Reset stop button
        BolusProgressData.stopPressed = false
        helpActivity?.finish()
    }

    override fun onPause() {
        super.onPause()
        aapsLogger.debug(LTag.UI, "onPause")
        running = false
        disposable.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("state", state)
        outState.putDouble("amount", amount)
        outState.putLong("id", id)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    private fun scheduleDismiss() {
        aapsLogger.debug(LTag.UI, "scheduleDismiss")
        Thread {
            SystemClock.sleep(5000)
            BolusProgressData.bolusEnded = true
            activity?.runOnUiThread {
                if (running) {
                    aapsLogger.debug(LTag.UI, "executing")
                    try {
                        dismiss()
                    } catch (e: Exception) {
                        aapsLogger.error("Unhandled exception", e)
                    }
                }
            }
        }.start()
    }
}
