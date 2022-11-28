package info.nightscout.plugins.sync.tidepool

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import dagger.android.support.DaggerFragment
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.plugins.sync.R
import info.nightscout.plugins.sync.databinding.TidepoolFragmentBinding
import info.nightscout.plugins.sync.tidepool.comm.TidepoolUploader
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolDoUpload
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolResetData
import info.nightscout.plugins.sync.tidepool.events.EventTidepoolUpdateGUI
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class TidepoolFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
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
