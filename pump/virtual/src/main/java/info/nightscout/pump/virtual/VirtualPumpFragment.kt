package info.nightscout.pump.virtual

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import dagger.android.support.DaggerFragment
import info.nightscout.core.extensions.toStringFull
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.interfaces.iob.IobCobCalculator
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.pump.virtual.databinding.VirtualPumpFragmentBinding
import info.nightscout.pump.virtual.events.EventVirtualPumpUpdateGui
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventExtendedBolusChange
import info.nightscout.rx.events.EventTempBasalChange
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class VirtualPumpFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var virtualPumpPlugin: VirtualPumpPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    private val disposable = CompositeDisposable()

    private lateinit var refreshLoop: Runnable
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var _binding: VirtualPumpFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        VirtualPumpFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventVirtualPumpUpdateGui::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        refreshLoop = Runnable {
            activity?.runOnUiThread { updateGui() }
            handler.postDelayed(refreshLoop, T.mins(1).msecs())
        }
        handler.postDelayed(refreshLoop, T.mins(1).msecs())
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
        handler.removeCallbacksAndMessages(null)
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @Synchronized
    private fun updateGui() {
        if (_binding == null) return
        val profile = profileFunction.getProfile() ?: return
        binding.baseBasalRate.text = rh.gs(info.nightscout.core.ui.R.string.pump_base_basal_rate, virtualPumpPlugin.baseBasalRate)
        binding.tempbasal.text = iobCobCalculator.getTempBasal(dateUtil.now())?.toStringFull(profile, dateUtil)
            ?: ""
        binding.extendedbolus.text = iobCobCalculator.getExtendedBolus(dateUtil.now())?.toStringFull(dateUtil)
            ?: ""
        binding.battery.text = rh.gs(info.nightscout.core.ui.R.string.format_percent, virtualPumpPlugin.batteryPercent)
        binding.reservoir.text = rh.gs(info.nightscout.interfaces.R.string.format_insulin_units, virtualPumpPlugin.reservoirInUnits.toDouble())

        virtualPumpPlugin.refreshConfiguration()
        val pumpType = virtualPumpPlugin.pumpType

        binding.type.text = pumpType?.description
        binding.typeDef.text = pumpType?.getFullDescription(rh.gs(R.string.virtual_pump_pump_def), pumpType.hasExtendedBasals(), rh)
        binding.serialNumber.text = virtualPumpPlugin.serialNumber()
    }
}
