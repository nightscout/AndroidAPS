package info.nightscout.plugins.general.wear

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.plugins.general.wear.events.EventWearUpdateGui
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.plugins.databinding.WearFragmentBinding
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventMobileToWear
import info.nightscout.rx.weardata.EventData
import info.nightscout.shared.utils.DateUtil
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class WearFragment : DaggerFragment() {

    @Inject lateinit var wearPlugin: WearPlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var dateUtil: DateUtil

    private var _binding: WearFragmentBinding? = null

    private val disposable = CompositeDisposable()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = WearFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.resend.setOnClickListener { rxBus.send(EventData.ActionResendData("WearFragment")) }
        binding.openSettings.setOnClickListener { rxBus.send(EventMobileToWear(EventData.OpenSettings(dateUtil.now()))) }
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventWearUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        updateGui()
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateGui() {
        _binding ?: return
        binding.connectedDevice.text = wearPlugin.connectedDevice
    }
}