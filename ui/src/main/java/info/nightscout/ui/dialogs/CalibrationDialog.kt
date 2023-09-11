package info.nightscout.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.GlucoseUnit
import info.nightscout.interfaces.XDripBroadcast
import info.nightscout.interfaces.iob.GlucoseStatusProvider
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.shared.interfaces.ProfileUtil
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.ui.databinding.DialogCalibrationBinding
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject

class CalibrationDialog : DialogFragmentWithDate() {

    @Inject lateinit var injector: HasAndroidInjector
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var xDripBroadcast: XDripBroadcast
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider

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
        binding.units.text = if (units == GlucoseUnit.MMOL) rh.gs(info.nightscout.core.ui.R.string.mmol) else rh.gs(info.nightscout.core.ui.R.string.mgdl)
        binding.bgLabel.labelFor = binding.bg.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val units = profileUtil.units
        val unitLabel = if (units == GlucoseUnit.MMOL) rh.gs(info.nightscout.core.ui.R.string.mmol) else rh.gs(info.nightscout.core.ui.R.string.mgdl)
        val actions: LinkedList<String?> = LinkedList()
        val bg = binding.bg.value
        actions.add(rh.gs(info.nightscout.core.ui.R.string.bg_label) + ": " + profileUtil.stringInCurrentUnitsDetect(bg) + " " + unitLabel)
        if (bg > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.calibration), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    uel.log(Action.CALIBRATION, Sources.CalibrationDialog, ValueWithUnit.fromGlucoseUnit(bg, units.asText))
                    xDripBroadcast.sendCalibration(bg)
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(info.nightscout.core.ui.R.string.calibration), rh.gs(info.nightscout.core.ui.R.string.no_action_selected))
            }
        return true
    }
}
