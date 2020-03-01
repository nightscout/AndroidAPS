package info.nightscout.androidaps.plugins.constraints.objectives.dialogs

import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus.toObservable
import info.nightscout.androidaps.plugins.constraints.objectives.events.EventNtpStatus
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.dialog_bolusprogress.*
import org.slf4j.LoggerFactory

class NtpProgressDialog : DialogFragment() {
    private val log = LoggerFactory.getLogger(L.UI)
    private val disposable = CompositeDisposable()

    private val DEFAULT_STATE = MainApp.gs(R.string.timedetection)
    private var state: String = DEFAULT_STATE
    private var percent = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        dialog?.setTitle(String.format(MainApp.gs(R.string.objectives)))
        isCancelable = false

        state = savedInstanceState?.getString("state", DEFAULT_STATE) ?: DEFAULT_STATE
        percent = savedInstanceState?.getInt("percent", 0) ?: 0

        return inflater.inflate(R.layout.dialog_bolusprogress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        overview_bolusprogress_stop.setOnClickListener { dismiss() }
        overview_bolusprogress_status.text = state
        overview_bolusprogress_progressbar.max = 100
        overview_bolusprogress_progressbar.progress = percent
        overview_bolusprogress_stop.text = MainApp.gs(R.string.close)
        overview_bolusprogress_title.text = MainApp.gs(R.string.please_wait)
    }

    override fun onResume() {
        super.onResume()
        if (L.isEnabled(L.UI)) log.debug("onResume")
        if (percent == 100) {
            dismiss()
            return
        } else
            dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        disposable.add(toObservable(EventNtpStatus::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ event: EventNtpStatus ->
                    if (L.isEnabled(L.UI)) log.debug("Status: " + event.status + " Percent: " + event.percent)
                    overview_bolusprogress_status?.text = event.status
                    overview_bolusprogress_progressbar?.progress = event.percent
                    if (event.percent == 100) {
                        SystemClock.sleep(100)
                        dismiss()
                    }
                    state = event.status
                    percent = event.percent
                }) { FabricPrivacy.logException(it) }
        )
    }

    override fun onPause() {
        if (L.isEnabled(L.UI)) log.debug("onPause")
        super.onPause()
        disposable.clear()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("state", state)
        outState.putInt("percent", percent)
        super.onSaveInstanceState(outState)
    }
}