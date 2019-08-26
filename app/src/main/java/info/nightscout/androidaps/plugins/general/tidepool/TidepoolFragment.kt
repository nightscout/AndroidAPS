package info.nightscout.androidaps.plugins.general.tidepool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolUpdateGUI
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.tidepool_fragment.*

class TidepoolFragment : Fragment() {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.tidepool_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tidepool_login.setOnClickListener { TidepoolUploader.doLogin(false) }
        tidepool_uploadnow.setOnClickListener { RxBus.send(EventTidepoolDoUpload()) }
        tidepool_removeall.setOnClickListener { RxBus.send(EventTidepoolResetData()) }
        tidepool_resertstart.setOnClickListener { SP.putLong(R.string.key_tidepool_last_end, 0) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(RxBus
                .toObservable(EventTidepoolUpdateGUI::class.java)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    TidepoolPlugin.updateLog()
                    tidepool_log?.text = TidepoolPlugin.textLog
                    tidepool_status?.text = TidepoolUploader.connectionStatus.name
                    tidepool_log?.text = TidepoolPlugin.textLog
                    tidepool_logscrollview?.fullScroll(ScrollView.FOCUS_DOWN)
                }, {
                    FabricPrivacy.logException(it)
                })
        )
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }
}
