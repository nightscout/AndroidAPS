package info.nightscout.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.extensions.formatColor
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.constraints.Constraint
import info.nightscout.interfaces.constraints.Constraints
import info.nightscout.interfaces.db.PersistenceLayer
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.pump.DetailedBolusInfo
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.interfaces.utils.DecimalFormatter
import info.nightscout.interfaces.utils.HtmlHelper
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.SafeParse
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.ui.R
import info.nightscout.ui.databinding.DialogTreatmentBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class TreatmentDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: Constraints
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var ctx: Context
    @Inject lateinit var config: Config
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var persistenceLayer: PersistenceLayer

    private var queryingProtection = false
    private val disposable = CompositeDisposable()
    private var _binding: DialogTreatmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            validateInputs()
        }
    }

    private fun validateInputs() {
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (SafeParse.stringToInt(binding.carbs.text) > maxCarbs) {
            binding.carbs.value = 0.0
            ToastUtils.warnToast(context, R.string.carbs_constraint_applied)
        }
        if (SafeParse.stringToDouble(binding.insulin.text) > maxInsulin) {
            binding.insulin.value = 0.0
            ToastUtils.warnToast(context, R.string.bolus_constraint_applied)
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("carbs", binding.carbs.value)
        savedInstanceState.putDouble("insulin", binding.insulin.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogTreatmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (config.NSCLIENT) {
            binding.recordOnly.isChecked = true
            binding.recordOnly.isEnabled = false
        }
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val pumpDescription = activePlugin.activePump.pumpDescription
        binding.carbs.setParams(
            savedInstanceState?.getDouble("carbs")
                ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )
        binding.insulin.setParams(
            savedInstanceState?.getDouble("insulin")
                ?: 0.0, 0.0, maxInsulin, pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, binding.okcancel.ok, textWatcher
        )
        binding.recordOnlyLayout.visibility = View.GONE
        binding.insulinLabel.labelFor = binding.insulin.editTextId
        binding.carbsLabel.labelFor = binding.carbs.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = SafeParse.stringToDouble(binding.insulin.text)
        val carbs = SafeParse.stringToInt(binding.carbs.text)
        val recordOnlyChecked = binding.recordOnly.isChecked
        val actions: LinkedList<String?> = LinkedList()
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()

        if (insulinAfterConstraints > 0) {
            actions.add(rh.gs(R.string.bolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, rh).formatColor(context, rh, R.attr.bolusColor))
            if (recordOnlyChecked)
                actions.add(rh.gs(R.string.bolus_recorded_only).formatColor(context, rh, R.attr.warningColor))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(rh.gs(R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints).formatColor(context, rh, R.attr.warningColor))
        }
        if (carbsAfterConstraints > 0) {
            actions.add(rh.gs(R.string.carbs) + ": " + rh.gs(R.string.format_carbs, carbsAfterConstraints).formatColor(context, rh, R.attr.carbsColor))
            if (carbsAfterConstraints != carbs)
                actions.add(rh.gs(R.string.carbs_constraint_applied).formatColor(context, rh, R.attr.warningColor))
        }
        if (insulinAfterConstraints > 0 || carbsAfterConstraints > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.overview_treatment_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    val action = when {
                        insulinAfterConstraints.equals(0.0) -> UserEntry.Action.CARBS
                        carbsAfterConstraints == 0          -> UserEntry.Action.BOLUS
                        else                                -> UserEntry.Action.TREATMENT
                    }
                    val detailedBolusInfo = DetailedBolusInfo()
                    if (insulinAfterConstraints == 0.0) detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CARBS_CORRECTION
                    if (carbsAfterConstraints == 0) detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
                    detailedBolusInfo.insulin = insulinAfterConstraints
                    detailedBolusInfo.carbs = carbsAfterConstraints.toDouble()
                    detailedBolusInfo.context = context
                    if (recordOnlyChecked) {
                        uel.log(action, UserEntry.Sources.TreatmentDialog, if (insulinAfterConstraints != 0.0) rh.gs(R.string.record) else "",
                                ValueWithUnit.Timestamp(detailedBolusInfo.timestamp).takeIf { eventTimeChanged },
                                ValueWithUnit.SimpleString(rh.gsNotLocalised(R.string.record)).takeIf { insulinAfterConstraints != 0.0 },
                                ValueWithUnit.Insulin(insulinAfterConstraints).takeIf { insulinAfterConstraints != 0.0 },
                                ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 })
                        if (detailedBolusInfo.insulin > 0)
                            persistenceLayer.insertOrUpdateBolus(detailedBolusInfo.createBolus())
                        if (detailedBolusInfo.carbs > 0)
                            persistenceLayer.insertOrUpdateCarbs(detailedBolusInfo.createCarbs())
                    } else {
                        if (detailedBolusInfo.insulin > 0) {
                            uel.log(action, UserEntry.Sources.TreatmentDialog,
                                    ValueWithUnit.Insulin(insulinAfterConstraints),
                                    ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 })
                            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        uiInteraction.runAlarm(result.comment, rh.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                                    }
                                }
                            })
                        } else {
                            uel.log(action, UserEntry.Sources.TreatmentDialog,
                                    ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 })
                            if (detailedBolusInfo.carbs > 0)
                                persistenceLayer.insertOrUpdateCarbs(detailedBolusInfo.createCarbs())
                        }
                    }
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(R.string.overview_treatment_label), rh.gs(R.string.no_action_selected))
            }
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}