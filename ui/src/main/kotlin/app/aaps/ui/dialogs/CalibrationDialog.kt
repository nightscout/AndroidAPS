package app.aaps.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sync.XDripBroadcast
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.ui.databinding.DialogCalibrationBinding
import com.google.common.base.Joiner
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject

class CalibrationDialog : DialogFragmentWithDate() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var xDripBroadcast: XDripBroadcast
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var uiInteraction: UiInteraction

    private var _binding: DialogCalibrationBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("bg", binding.bg.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogCalibrationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val units = profileUtil.units
        val bg = profileUtil.fromMgdlToUnits(glucoseStatusProvider.glucoseStatusData?.glucose ?: 0.0)
        if (units == GlucoseUnit.MMOL)
            binding.bg.setParams(
                savedInstanceState?.getDouble("bg")
                    ?: bg, 2.0, 30.0, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok
            )
        else
            binding.bg.setParams(
                savedInstanceState?.getDouble("bg")
                    ?: bg, 36.0, 500.0, 1.0, DecimalFormat("0"), false, binding.okcancel.ok
            )
        binding.units.text = if (units == GlucoseUnit.MMOL) rh.gs(app.aaps.core.ui.R.string.mmol) else rh.gs(app.aaps.core.ui.R.string.mgdl)
        binding.bgLabel.labelFor = binding.bg.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val units = profileUtil.units
        val unitLabel = if (units == GlucoseUnit.MMOL) rh.gs(app.aaps.core.ui.R.string.mmol) else rh.gs(app.aaps.core.ui.R.string.mgdl)
        val actions: LinkedList<String?> = LinkedList()
        val bg = binding.bg.value
        actions.add(rh.gs(app.aaps.core.ui.R.string.bg_label) + ": " + profileUtil.stringInCurrentUnitsDetect(bg) + " " + unitLabel)
        if (bg > 0) {
            uiInteraction.showOkCancelDialog(
                context = requireActivity(),
                title = rh.gs(app.aaps.core.ui.R.string.calibration),
                message = Joiner.on("<br/>").join(actions),
                ok = {
                    uel.log(action = Action.CALIBRATION, source = Sources.CalibrationDialog, value = ValueWithUnit.fromGlucoseUnit(bg, units))
                    xDripBroadcast.sendCalibration(bg)
                }
            )
        } else
            uiInteraction.showOkDialog(
                context = requireActivity(),
                title = rh.gs(app.aaps.core.ui.R.string.calibration),
                message = rh.gs(app.aaps.core.ui.R.string.no_action_selected)
            )
        return true
    }
}
