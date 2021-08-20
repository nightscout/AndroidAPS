package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.core.databinding.DialogBolusprogressBinding
import info.nightscout.androidaps.events.EventNtpStatus
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class NtpProgressDialog : DaggerDialogFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private val disposable = CompositeDisposable()

    private var state: String? = null
    private var percent = 0

    private var _binding: DialogBolusprogressBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        isCancelable = false

        state = savedInstanceState?.getString("state", null)
        percent = savedInstanceState?.getInt("percent", 0) ?: 0

        _binding = DialogBolusprogressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val defaultMessage = resourceHelper.gs(R.string.timedetection)
        dialog?.setTitle(resourceHelper.gs(R.string.objectives))
        binding.stop.setOnClickListener { dismiss() }
        binding.status.text = state ?: defaultMessage
        binding.progressbar.max = 100
        binding.progressbar.progress = percent
        binding.stop.text = resourceHelper.gs(R.string.close)
        binding.title.text = resourceHelper.gs(R.string.please_wait)
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
            .observeOn(AndroidSchedulers.mainThread())
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