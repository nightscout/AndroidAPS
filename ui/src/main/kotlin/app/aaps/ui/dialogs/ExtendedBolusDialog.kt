package app.aaps.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogExtendedbolusBinding
import com.google.common.base.Joiner
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class ExtendedBolusDialog : DialogFragmentWithDate() {

    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private var queryingProtection = false
    private var _binding: DialogExtendedbolusBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("insulin", binding.insulin.value)
        savedInstanceState.putDouble("duration", binding.duration.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogExtendedbolusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pumpDescription = activePlugin.activePump.pumpDescription

        val maxInsulin = constraintChecker.getMaxExtendedBolusAllowed().value()
        val extendedStep = pumpDescription.extendedBolusStep
        binding.insulin.setParams(
            savedInstanceState?.getDouble("insulin")
                ?: extendedStep, extendedStep, maxInsulin, extendedStep, DecimalFormat("0.00"), false, binding.okcancel.ok
        )

        val extendedDurationStep = pumpDescription.extendedBolusDurationStep
        val extendedMaxDuration = pumpDescription.extendedBolusMaxDuration
        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
                ?: extendedDurationStep, extendedDurationStep, extendedMaxDuration, extendedDurationStep, DecimalFormat("0"), false, binding.okcancel.ok
        )
        binding.insulinLabel.labelFor = binding.insulin.editTextId
        binding.durationLabel.labelFor = binding.duration.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val insulin = SafeParse.stringToDouble(binding.insulin.text)
        val durationInMinutes = binding.duration.value.toInt()
        val actions: LinkedList<String> = LinkedList()
        val insulinAfterConstraint = constraintChecker.applyExtendedBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        actions.add(rh.gs(app.aaps.core.ui.R.string.format_insulin_units, insulinAfterConstraint))
        actions.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, durationInMinutes))
        if (abs(insulinAfterConstraint - insulin) > 0.01)
            actions.add(rh.gs(app.aaps.core.ui.R.string.constraint_applied).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))

        uiInteraction.showOkCancelDialog(
            context = requireActivity(),
            title = rh.gs(app.aaps.core.ui.R.string.extended_bolus),
            message = Joiner.on("<br/>").join(actions),
            ok = {
                uel.log(
                    action = Action.EXTENDED_BOLUS, source = Sources.ExtendedBolusDialog,
                    listValues = listOf(
                        ValueWithUnit.Insulin(insulinAfterConstraint),
                        ValueWithUnit.Minute(durationInMinutes)
                    )
                )
                commandQueue.extendedBolus(insulinAfterConstraint, durationInMinutes, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                        }
                    }
                })
            },
            cancel = null
        )
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            val cancelFail = {
                queryingProtection = false
                aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                ToastUtils.warnToast(requireContext(), R.string.dialog_canceled)
                dismiss()
            }
            protectionCheck.queryProtection(requireActivity(), ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
        }
    }
}