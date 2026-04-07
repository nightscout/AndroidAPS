package app.aaps.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import app.aaps.core.data.model.BS
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogFillBinding
import com.google.common.base.Joiner
import kotlinx.coroutines.launch
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class FillDialog(val fm: FragmentManager) : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var ch: ConcentrationHelper

    private var queryingProtection = false
    private var _binding: DialogFillBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("fill_insulin_amount", binding.fillInsulinAmount.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogFillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        binding.fillInsulinAmount.setParams(
            savedInstanceState?.getDouble("fill_insulin_amount")
                ?: 0.0, 0.0, maxInsulin, bolusStep, decimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump.pumpDescription.bolusStep), true, binding.okcancel.ok
        )
        val amount1 = preferences.get(DoubleKey.ActionsFillButton1)
        if (amount1 > 0) {
            binding.fillPresetButton1.visibility = View.VISIBLE
            binding.fillPresetButton1.text = decimalFormatter.toPumpSupportedBolus(amount1, activePlugin.activePump.pumpDescription.bolusStep) // + "U");
            binding.fillPresetButton1.setOnClickListener { binding.fillInsulinAmount.value = amount1 }
        } else {
            binding.fillPresetButton1.visibility = View.GONE
        }
        val amount2 = preferences.get(DoubleKey.ActionsFillButton2)
        if (amount2 > 0) {
            binding.fillPresetButton2.visibility = View.VISIBLE
            binding.fillPresetButton2.text = decimalFormatter.toPumpSupportedBolus(amount2, activePlugin.activePump.pumpDescription.bolusStep) // + "U");
            binding.fillPresetButton2.setOnClickListener { binding.fillInsulinAmount.value = amount2 }
        } else {
            binding.fillPresetButton2.visibility = View.GONE
        }
        val amount3 = preferences.get(DoubleKey.ActionsFillButton3)
        if (amount3 > 0) {
            binding.fillPresetButton3.visibility = View.VISIBLE
            binding.fillPresetButton3.text = decimalFormatter.toPumpSupportedBolus(amount3, activePlugin.activePump.pumpDescription.bolusStep) // + "U");
            binding.fillPresetButton3.setOnClickListener { binding.fillInsulinAmount.value = amount3 }
        } else {
            binding.fillPresetButton3.visibility = View.GONE
        }
        binding.fillLabel.labelFor = binding.fillInsulinAmount.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val insulin = SafeParse.stringToDouble(binding.fillInsulinAmount.text)
        val siteChange = binding.fillCatheterChange.isChecked
        val insulinChange = binding.fillCartridgeChange.isChecked
        val actions: LinkedList<String?> = LinkedList()

        var insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        if (insulinAfterConstraints > 0) {
            actions.add(rh.gs(R.string.fill_warning))
            actions.add("")
            actions.add(
                if (siteChange || insulinChange)    // include volume information in µl
                    rh.gs(app.aaps.core.ui.R.string.bolus) + ": " + ch.bolusWithVolume(insulinAfterConstraints).formatColor(context, rh, app.aaps.core.ui.R.attr.insulinButtonColor)
                else
                    rh.gs(app.aaps.core.ui.R.string.bolus) + ": " + decimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump.pumpDescription.bolusStep)
                        .formatColor(context, rh, app.aaps.core.ui.R.attr.insulinButtonColor)
            )
            if (abs(insulinAfterConstraints - insulin) > 0.01)
                actions.add(
                    rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor)
                )
            if (!ch.isU100()) {
                actions.add(rh.gs(R.string.fill_pump_units_note).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))
            }
        }
        if (siteChange)
            actions.add(rh.gs(R.string.record_pump_site_change).formatColor(context, rh, app.aaps.core.ui.R.attr.actionsConfirmColor))
        if (insulinChange)
            actions.add(rh.gs(R.string.record_insulin_cartridge_change).formatColor(context, rh, app.aaps.core.ui.R.attr.actionsConfirmColor))
        val notes: String = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(rh.gs(app.aaps.core.ui.R.string.notes_label) + ": " + notes)
        eventTime -= eventTime % 1000

        if (eventTimeChanged)
            actions.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        if (insulinAfterConstraints > 0 || binding.fillCatheterChange.isChecked || binding.fillCartridgeChange.isChecked) {
            uiInteraction.showOkCancelDialog(
                context = requireActivity(),
                title = rh.gs(app.aaps.core.ui.R.string.prime_fill),
                message = Joiner.on("<br/>").join(actions),
                ok = {
                    if (insulinAfterConstraints > 0) {
                        uel.log(
                            action = Action.PRIME_BOLUS, source = Sources.FillDialog,
                            note = notes,
                            value = ValueWithUnit.Insulin(insulinAfterConstraints)
                        )
                        requestPrimeBolus(insulinAfterConstraints, notes)
                    }
                    if (siteChange) {
                        lifecycleScope.launch {
                            persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                                therapyEvent = TE(
                                    timestamp = eventTime,
                                    type = TE.Type.CANNULA_CHANGE,
                                    note = notes,
                                    glucoseUnit = GlucoseUnit.MGDL
                                ),
                                action = Action.SITE_CHANGE, source = Sources.FillDialog,
                                note = notes,
                                listValues = listOfNotNull(
                                    ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                                    ValueWithUnit.TEType(TE.Type.CANNULA_CHANGE)
                                )
                            )
                        }
                    }
                    if (insulinChange)
                    // add a second for case of both checked
                        lifecycleScope.launch {
                            persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                                therapyEvent = TE(
                                    timestamp = eventTime + 1000,
                                    type = TE.Type.INSULIN_CHANGE,
                                    note = notes,
                                    glucoseUnit = GlucoseUnit.MGDL
                                ),
                                action = Action.RESERVOIR_CHANGE, source = Sources.FillDialog,
                                note = notes,
                                listValues = listOfNotNull(
                                    ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                                    ValueWithUnit.TEType(TE.Type.INSULIN_CHANGE)
                                )
                            )
                        }
                },
                cancel = null
            )
        } else {
            uiInteraction.showOkDialog(
                context = requireActivity(),
                title = rh.gs(app.aaps.core.ui.R.string.prime_fill),
                message = rh.gs(app.aaps.core.ui.R.string.no_action_selected)
            )
        }
        dismiss()
        return true
    }

    private fun requestPrimeBolus(insulin: Double, notes: String) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = insulin
        detailedBolusInfo.bolusType = BS.Type.PRIMING
        detailedBolusInfo.notes = notes
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                if (!result.success) {
                    uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(activity, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}