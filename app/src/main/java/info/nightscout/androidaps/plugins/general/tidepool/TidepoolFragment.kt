package info.nightscout.androidaps.plugins.general.tidepool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolUpdateGUI
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.tidepool_fragment.*
import javax.inject.Inject

class TidepoolFragment : DaggerFragment() {
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var tidepoolPlugin: TidepoolPlugin
    @Inject lateinit var tidepoolUploader: TidepoolUploader
    @Inject lateinit var sp: SP
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.tidepool_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tidepool_login.setOnClickListener { tidepoolUploader.doLogin(false) }
        tidepool_uploadnow.setOnClickListener { rxBus.send(EventTidepoolDoUpload()) }
        tidepool_removeall.setOnClickListener { rxBus.send(EventTidepoolResetData()) }
        tidepool_resertstart.setOnClickListener { sp.putLong(R.string.key_tidepool_last_end, 0) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventTidepoolUpdateGUI::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                tidepoolPlugin.updateLog()
                tidepool_log?.text = tidepoolPlugin.textLog
                tidepool_status?.text = tidepoolUploader.connectionStatus.name
                tidepool_log?.text = tidepoolPlugin.textLog
                tidepool_logscrollview?.fullScroll(ScrollView.FOCUS_DOWN)
            }, { fabricPrivacy.logException(it) })
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }
}
