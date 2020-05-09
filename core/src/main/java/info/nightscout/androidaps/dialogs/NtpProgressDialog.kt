package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerDialogFragment
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.events.EventNtpStatus
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_bolusprogress.*
import javax.inject.Inject

class NtpProgressDialog : DaggerDialogFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private val disposable = CompositeDisposable()

    private var state: String? = null
    private var percent = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        isCancelable = false

        state = savedInstanceState?.getString("state", null)
        percent = savedInstanceState?.getInt("percent", 0) ?: 0

        return inflater.inflate(R.layout.dialog_bolusprogress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val defaultMessage = resourceHelper.gs(R.string.timedetection)
        dialog?.setTitle(resourceHelper.gs(R.string.objectives))
        overview_bolusprogress_stop.setOnClickListener { dismiss() }
        overview_bolusprogress_status.text = state ?: defaultMessage
        overview_bolusprogress_progressbar.max = 100
        overview_bolusprogress_progressbar.progress = percent
        overview_bolusprogress_stop.text = resourceHelper.gs(R.string.close)
        overview_bolusprogress_title.text = resourceHelper.gs(R.string.please_wait)
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
                aapsLogger.debug(LTag.UI, "Status: " + event.status + " Percent: " + event.percent)
                overview_bolusprogress_status?.text = event.status
                overview_bolusprogress_progressbar?.progress = event.percent
                if (event.percent == 100) {
                    SystemClock.sleep(100)
                    dismiss()
                }
                state = event.status
                percent = event.percent
            }) { fabricPrivacy.logException(it) }
    }

    override fun onPause() {
        aapsLogger.debug(LTag.UI, "onPause")
        super.onPause()
        disposable.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("state", state)
        outState.putInt("percent", percent)
        super.onSaveInstanceState(outState)
    }
}