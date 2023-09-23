package info.nightscout.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import info.nightscout.core.constraints.ConstraintObject
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.HtmlHelper
import info.nightscout.core.utils.extensions.formatColor
import info.nightscout.database.entities.UserEntry
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.interfaces.constraints.ConstraintsChecker
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.protection.ProtectionCheck
import info.nightscout.interfaces.queue.Callback
import info.nightscout.interfaces.queue.CommandQueue
import info.nightscout.interfaces.ui.UiInteraction
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.SafeParse
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.ui.R
import info.nightscout.ui.databinding.DialogExtendedbolusBinding
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class ExtendedBolusDialog : DialogFragmentWithDate() {

    @Inject lateinit var ctx: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var injector: HasAndroidInjector

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
        actions.add(rh.gs(info.nightscout.core.ui.R.string.format_insulin_units, insulinAfterConstraint))
        actions.add(rh.gs(info.nightscout.core.ui.R.string.duration) + ": " + rh.gs(info.nightscout.core.ui.R.string.format_mins, durationInMinutes))
        if (abs(insulinAfterConstraint - insulin) > 0.01)
            actions.add(rh.gs(info.nightscout.core.ui.R.string.constraint_applied).formatColor(context, rh, info.nightscout.core.ui.R.attr.warningColor))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.extended_bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                uel.log(
                    UserEntry.Action.EXTENDED_BOLUS, UserEntry.Sources.ExtendedBolusDialog,
                    ValueWithUnit.Insulin(insulinAfterConstraint),
                    ValueWithUnit.Minute(durationInMinutes)
                )
                commandQueue.extendedBolus(insulinAfterConstraint, durationInMinutes, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(info.nightscout.core.ui.R.string.treatmentdeliveryerror), info.nightscout.core.ui.R.raw.boluserror)
                        }
                    }
                })
            }, null)
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