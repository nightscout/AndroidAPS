package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.model.TE
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogTreatmentBinding
import com.google.common.base.Joiner
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class TreatmentDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var ctx: Context
    @Inject lateinit var config: Config
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var queryingProtection = false
    private var _binding: DialogTreatmentBinding? = null

    private val disposable = CompositeDisposable()

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

        if (config.AAPSCLIENT) {
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
                ?: 0.0,
            0.0,
            maxInsulin,
            pumpDescription.bolusStep,
            decimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump.pumpDescription.bolusStep),
            false,
            binding.okcancel.ok,
            textWatcher
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
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(carbs, aapsLogger)).value()

        if (insulinAfterConstraints > 0) {
            actions.add(
                rh.gs(app.aaps.core.ui.R.string.bolus) + ": " + decimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump.pumpDescription.bolusStep)
                    .formatColor(
                        context, rh,
                        app.aaps.core.ui.R.attr.bolusColor
                    )
            )
            if (recordOnlyChecked)
                actions.add(rh.gs(app.aaps.core.ui.R.string.bolus_recorded_only).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(
                    rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor)
                )
        }
        if (carbsAfterConstraints > 0) {
            actions.add(
                rh.gs(app.aaps.core.ui.R.string.carbs) + ": " + rh.gs(app.aaps.core.objects.R.string.format_carbs, carbsAfterConstraints).formatColor(
                    context, rh, app.aaps.core.ui.R.attr.carbsColor
                )
            )
            if (carbsAfterConstraints != carbs)
                actions.add(rh.gs(R.string.carbs_constraint_applied).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))
        }
        if (insulinAfterConstraints > 0 || carbsAfterConstraints > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    val action = when {
                        insulinAfterConstraints.equals(0.0) -> Action.CARBS
                        carbsAfterConstraints == 0          -> Action.BOLUS
                        else                                -> Action.TREATMENT
                    }
                    val detailedBolusInfo = DetailedBolusInfo()
                    if (insulinAfterConstraints == 0.0) detailedBolusInfo.eventType = TE.Type.CARBS_CORRECTION
                    if (carbsAfterConstraints == 0) detailedBolusInfo.eventType = TE.Type.CORRECTION_BOLUS
                    detailedBolusInfo.insulin = insulinAfterConstraints
                    detailedBolusInfo.carbs = carbsAfterConstraints.toDouble()
                    detailedBolusInfo.context = context
                    if (recordOnlyChecked) {
                        if (detailedBolusInfo.insulin > 0)
                            disposable += persistenceLayer.insertOrUpdateBolus(
                                bolus = detailedBolusInfo.createBolus(),
                                action = action,
                                source = Sources.TreatmentDialog,
                                note = if (insulinAfterConstraints != 0.0) rh.gs(app.aaps.core.ui.R.string.record) else ""
                            ).subscribe()
                        if (detailedBolusInfo.carbs > 0)
                            disposable += persistenceLayer.insertOrUpdateCarbs(
                                carbs = detailedBolusInfo.createCarbs(),
                                action = action,
                                source = Sources.TreatmentDialog,
                                note = if (carbsAfterConstraints != 0) rh.gs(app.aaps.core.ui.R.string.record) else ""
                            ).subscribe()
                    } else {
                        if (detailedBolusInfo.insulin > 0) {
                            uel.log(
                                action = action,
                                source = Sources.TreatmentDialog,
                                listValues = listOf(
                                    ValueWithUnit.Insulin(insulinAfterConstraints),
                                    ValueWithUnit.Gram(carbsAfterConstraints).takeIf { carbsAfterConstraints != 0 }
                                ).filterNotNull()
                            )
                            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                                    }
                                }
                            })
                        } else {
                            if (detailedBolusInfo.carbs > 0)
                                disposable += persistenceLayer.insertOrUpdateCarbs(
                                    detailedBolusInfo.createCarbs(),
                                    action = action,
                                    source = Sources.TreatmentDialog
                                ).subscribe()
                        }
                    }
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(app.aaps.core.ui.R.string.overview_treatment_label), rh.gs(app.aaps.core.ui.R.string.no_action_selected))
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