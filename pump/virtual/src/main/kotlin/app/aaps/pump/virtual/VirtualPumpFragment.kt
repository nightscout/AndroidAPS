package app.aaps.pump.virtual

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.pump.defs.DoseStepSize
import app.aaps.core.data.pump.defs.PumpTempBasalType
import app.aaps.core.data.pump.defs.PumpType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.defs.baseBasalRange
import app.aaps.core.interfaces.pump.defs.hasExtendedBasals
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.pump.virtual.databinding.VirtualPumpFragmentBinding
import app.aaps.pump.virtual.events.EventVirtualPumpUpdateGui
import app.aaps.pump.virtual.keys.VirtualBooleanNonPreferenceKey
import dagger.android.support.DaggerFragment
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
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var preferences: Preferences

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

        binding.pumpSuspended.isChecked = preferences.get(VirtualBooleanNonPreferenceKey.IsSuspended)
        binding.pumpSuspended.setOnClickListener { preferences.put(VirtualBooleanNonPreferenceKey.IsSuspended, binding.pumpSuspended.isChecked) }

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

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        handler.looper.quitSafely()
    }

    @Synchronized
    private fun updateGui() {
        if (_binding == null) return
        val profile = profileFunction.getProfile() ?: return
        binding.baseBasalRate.text = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, virtualPumpPlugin.baseBasalRate)
        binding.tempbasal.text = persistenceLayer.getTemporaryBasalActiveAt(dateUtil.now())?.toStringFull(profile, dateUtil, rh)
            ?: ""
        binding.extendedbolus.text = persistenceLayer.getExtendedBolusActiveAt(dateUtil.now())?.toStringFull(dateUtil, rh)
            ?: ""
        binding.battery.text = rh.gs(app.aaps.core.ui.R.string.format_percent, virtualPumpPlugin.batteryPercent)
        binding.reservoir.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, virtualPumpPlugin.reservoirInUnits.toDouble())

        virtualPumpPlugin.refreshConfiguration()
        val pumpType = virtualPumpPlugin.pumpType

        binding.type.text = pumpType?.description
        binding.typeDef.text = pumpType?.getFullDescription(rh.gs(R.string.virtual_pump_pump_def), pumpType.hasExtendedBasals(), rh)
        binding.serialNumber.text = virtualPumpPlugin.serialNumber()
    }

    private fun getStep(step: String, stepSize: DoseStepSize?): String =
        if (stepSize != null) step + " [" + stepSize.description + "] *"
        else step

    private fun PumpType.getFullDescription(i18nTemplate: String, hasExtendedBasals: Boolean, rh: ResourceHelper): String {
        val unit = if (pumpTempBasalType() == PumpTempBasalType.Percent) "%" else ""
        val eb = extendedBolusSettings() ?: return "INVALID"
        val tbr = tbrSettings() ?: return "INVALID"
        val extendedNote = if (hasExtendedBasals) rh.gs(R.string.def_extended_note) else ""
        return String.format(
            i18nTemplate,
            getStep(bolusSize().toString(), specialBolusSize()),
            eb.step, eb.durationStep, eb.maxDuration / 60,
            getStep(baseBasalRange(), baseBasalSpecialSteps()),
            tbr.minDose.toString() + unit + "-" + tbr.maxDose + unit, tbr.step.toString() + unit,
            tbr.durationStep, tbr.maxDuration / 60, extendedNote
        )
    }
}
