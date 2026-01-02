package app.aaps.pump.danars.dialogs

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.pump.danars.activities.PairingHelperActivity
import app.aaps.pump.danars.databinding.DanarsPairingProgressDialogBinding
import app.aaps.pump.danars.events.EventDanaRSPairingSuccess
import dagger.android.support.DaggerDialogFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class PairingProgressDialog : DaggerDialogFragment() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private val disposable = CompositeDisposable()
    private var helperActivity: PairingHelperActivity? = null

    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private lateinit var runnable: Runnable

    private var _binding: DanarsPairingProgressDialogBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    companion object {

        private var pairingEnded = false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DanarsPairingProgressDialogBinding.inflate(inflater, container, false)
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)
        isCancelable = false
        dialog?.setCanceledOnTouchOutside(false)
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventDanaRSPairingSuccess::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ pairingEnded = true }, fabricPrivacy::logException)
        if (pairingEnded) dismiss()
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        runnable = Runnable {
            for (i in 0..19) {
                if (pairingEnded) {
                    activity?.runOnUiThread {
                        _binding?.danarsPairingProgressProgressbar?.progress = 100
                        _binding?.danarsPairingProgressStatus?.setText(app.aaps.pump.dana.R.string.danars_pairingok)
                        handler.postDelayed({ dismiss() }, 1000)
                    }
                    return@Runnable
                }
                _binding?.danarsPairingProgressProgressbar?.progress = i * 5
                SystemClock.sleep(1000)
            }
            activity?.runOnUiThread {
                _binding?.danarsPairingProgressProgressbar?.progress = 100
                _binding?.danarsPairingProgressStatus?.setText(app.aaps.pump.dana.R.string.danars_pairingtimedout)
                _binding?.ok?.visibility = View.VISIBLE
            }
        }
        setViews()
    }

    override fun dismiss() {
        super.dismissAllowingStateLoss()
        helperActivity?.finish()
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setViews() {
        _binding?.danarsPairingProgressProgressbar?.max = 100
        _binding?.danarsPairingProgressProgressbar?.progress = 0
        _binding?.danarsPairingProgressStatus?.text = rh.gs(app.aaps.pump.dana.R.string.danars_waitingforpairing)
        _binding?.ok?.visibility = View.GONE
        _binding?.ok?.setOnClickListener { dismiss() }
        handler.post(runnable)
    }

    fun resetToNewPairing() {
        handler.removeCallbacks(runnable)
        setViews()
    }

    fun setHelperActivity(activity: PairingHelperActivity?): PairingProgressDialog {
        helperActivity = activity
        return this
    }
}