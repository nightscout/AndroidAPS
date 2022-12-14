package info.nightscout.plugins.constraints.objectives.dialogs

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerDialogFragment
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.plugins.constraints.databinding.DialogNtpProgressBinding
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNtpStatus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.interfaces.ResourceHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class NtpProgressDialog : DaggerDialogFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val disposable = CompositeDisposable()

    private var state: String? = null
    private var percent = 0

    private var _binding: DialogNtpProgressBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        isCancelable = false

        state = savedInstanceState?.getString("state", null)
        percent = savedInstanceState?.getInt("percent", 0) ?: 0

        _binding = DialogNtpProgressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val defaultMessage = rh.gs(info.nightscout.core.ui.R.string.timedetection)
        dialog?.setTitle(rh.gs(info.nightscout.core.ui.R.string.objectives))
        binding.stop.setOnClickListener { dismiss() }
        binding.status.text = state ?: defaultMessage
        binding.progressbar.max = 100
        binding.progressbar.progress = percent
        binding.stop.text = rh.gs(info.nightscout.core.ui.R.string.close)
        binding.title.text = rh.gs(info.nightscout.core.ui.R.string.please_wait)
    }

    override fun onResume() {
        super.onResume()
        aapsLogger.debug(LTag.UI, "onResume")
        if (percent == 100) {
            dismiss()
            return
        } else
            dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        disposable += rxBus
            .toObservable(EventNtpStatus::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ event: EventNtpStatus ->
                if (_binding != null) {
                    aapsLogger.debug(LTag.UI, "Status: " + event.status + " Percent: " + event.percent)
                    binding.status.text = event.status
                    binding.progressbar.progress = event.percent
                    if (event.percent == 100) {
                        SystemClock.sleep(100)
                        dismiss()
                    }
                    state = event.status
                    percent = event.percent
                }
            }, fabricPrivacy::logException)
    }

    override fun onPause() {
        aapsLogger.debug(LTag.UI, "onPause")
        super.onPause()
        disposable.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("state", state)
        outState.putInt("percent", percent)
        super.onSaveInstanceState(outState)
    }
}