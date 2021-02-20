package info.nightscout.androidaps.plugins.general.tidepool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.TidepoolFragmentBinding
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.tidepool.comm.TidepoolUploader
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolDoUpload
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolResetData
import info.nightscout.androidaps.plugins.general.tidepool.events.EventTidepoolUpdateGUI
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class TidepoolFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var tidepoolPlugin: TidepoolPlugin
    @Inject lateinit var tidepoolUploader: TidepoolUploader
    @Inject lateinit var sp: SP
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private var disposable: CompositeDisposable = CompositeDisposable()

    private var _binding: TidepoolFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = TidepoolFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.login.setOnClickListener { tidepoolUploader.doLogin(false) }
        binding.uploadnow.setOnClickListener { rxBus.send(EventTidepoolDoUpload()) }
        binding.removeall.setOnClickListener { rxBus.send(EventTidepoolResetData()) }
        binding.resertstart.setOnClickListener { sp.putLong(R.string.key_tidepool_last_end, 0) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventTidepoolUpdateGUI::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                if (_binding == null) return@subscribe
                tidepoolPlugin.updateLog()
                binding.log.text = tidepoolPlugin.textLog
                binding.status.text = tidepoolUploader.connectionStatus.name
                binding.log.text = tidepoolPlugin.textLog
                binding.logscrollview.fullScroll(ScrollView.FOCUS_DOWN)
            }, fabricPrivacy::logException)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

}
