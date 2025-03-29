package app.aaps.pump.apex.ui

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventInitializationChanged
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventQueueChanged
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.pump.apex.ApexPump
import app.aaps.pump.apex.R
import app.aaps.pump.apex.databinding.ApexFragmentBinding
import app.aaps.pump.apex.events.EventApexPumpDataChanged
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ApexFragment : DaggerFragment() {
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var pump: ApexPump
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy

    private val disposable = CompositeDisposable()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)
    private var refreshLoop: Runnable = Runnable {
        activity?.runOnUiThread { updateGUI() }
    }

    private var _binding: ApexFragmentBinding? = null
    val binding: ApexFragmentBinding
        get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        _binding = ApexFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun onResume() {
        super.onResume()

        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventApexPumpDataChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ updateGUI() }, fabricPrivacy::logException)
        for (clazz in listOf(
            EventInitializationChanged::class.java,
            EventPumpStatusChanged::class.java,
            EventApexPumpDataChanged::class.java,
            EventPreferenceChange::class.java,
            EventTempBasalChange::class.java,
            EventQueueChanged::class.java,
        )) {
            disposable += rxBus
                .toObservable(clazz)
                .observeOn(aapsSchedulers.io)
                .subscribe({ updateGUI() }, fabricPrivacy::logException)
        }

        updateGUI()
        handler.postDelayed(refreshLoop, T.mins(1).msecs())
    }

    override fun onPause() {
        disposable.clear()
        handler.removeCallbacks(refreshLoop)
        super.onPause()
    }

    private fun updateGUI() {
        aapsLogger.error(LTag.UI, "updateGUI")
        val status = pump.status
        if (status == null) aapsLogger.error(LTag.UI, "No status available!")

        binding.connectionStatus.text = when {
            activePlugin.activePump.isConnected() -> rh.gs(R.string.overview_connection_status_connected)
            activePlugin.activePump.isConnecting() -> rh.gs(R.string.overview_connection_status_connecting)
            else -> rh.gs(R.string.overview_connection_status_disconnected)
        }
        binding.serialNumber.text = pump.serialNumber
        binding.pumpStatus.text = status?.getPumpStatus(rh) ?: "?"
        binding.battery.text = status?.getBatteryLevel(rh) ?: "?"
        binding.reservoir.text = status?.getReservoirLevel(rh) ?: "?"
        binding.tempbasal.text = status?.getTBR(rh) ?: "?"
        binding.baseBasalRate.text = status?.getBasal(rh) ?: "?"
        binding.firmwareVersion.text =  pump.firmwareVersion?.toLocalString(rh) ?: "?"
        binding.lastBolus.text = pump.lastBolus?.toShortLocalString(rh) ?: "?"
    }
}
