package info.nightscout.androidaps.plugins.general.tidepool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.squareup.otto.Subscribe
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.common.SubscriberFragment
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientUpdateGUI
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import kotlinx.android.synthetic.main.tidepool_fragment.*

class TidepoolFragment : SubscriberFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.tidepool_fragment, container, false)

        tidepool_login.setOnClickListener {
            TidepoolUploader.doLogin()
        }
        tidepool_removeall.setOnClickListener { }
        tidepool_uploadnow.setOnClickListener { }
        return view
    }

    @Subscribe
    fun onStatusEvent(ev: EventNSClientUpdateGUI) {
        updateGUI()
    }

    override fun updateGUI() {
        val activity = activity
        activity?.runOnUiThread {
//            TidepoolPlugin.updateLog()
//            tidepool_log.text = TidepoolPlugin.textLog
        }
    }

}
