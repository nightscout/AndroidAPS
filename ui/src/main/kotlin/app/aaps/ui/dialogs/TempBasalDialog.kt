package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.pump.defs.PumpDescription
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.PumpSync
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogTempbasalBinding
import com.google.common.base.Joiner
import java.text.DecimalFormat
import java.util.LinkedList
import javax.inject.Inject
import kotlin.math.abs

class TempBasalDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var ctx: Context
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private var queryingProtection = false
    private var isPercentPump = true
    private var _binding: DialogTempbasalBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putDouble("basalPercentInput", binding.basalPercentInput.value)
        savedInstanceState.putDouble("basalAbsoluteInput", binding.basalAbsoluteInput.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogTempbasalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pumpDescription = activePlugin.activePump.pumpDescription
        val profile = profileFunction.getProfile() ?: return

        val maxTempPercent = pumpDescription.maxTempPercent.toDouble()
        val tempPercentStep = pumpDescription.tempPercentStep.toDouble()

        binding.basalPercentInput.setParams(
            savedInstanceState?.getDouble("basalPercentInput")
                ?: 100.0, 0.0, maxTempPercent, tempPercentStep, DecimalFormat("0"), true, binding.okcancel.ok
        )

        binding.basalAbsoluteInput.setParams(
            savedInstanceState?.getDouble("basalAbsoluteInput")
                ?: profile.getBasal(), 0.0, pumpDescription.maxTempAbsolute, pumpDescription.tempAbsoluteStep, DecimalFormat("0.00"), true, binding.okcancel.ok
        )

        val tempDurationStep = pumpDescription.tempDurationStep.toDouble()
        val tempMaxDuration = pumpDescription.tempMaxDuration.toDouble()
        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
                ?: tempDurationStep, tempDurationStep, tempMaxDuration, tempDurationStep, DecimalFormat("0"), false, binding.okcancel.ok
        )

        isPercentPump = pumpDescription.tempBasalStyle and PumpDescription.PERCENT == PumpDescription.PERCENT
        if (isPercentPump) {
            binding.percentLayout.visibility = View.VISIBLE
            binding.absoluteLayout.visibility = View.GONE
        } else {
            binding.percentLayout.visibility = View.GONE
            binding.absoluteLayout.visibility = View.VISIBLE
        }
        binding.basalPercentLabel.labelFor = binding.basalPercentInput.editTextId
        binding.basalAbsoluteLabel.labelFor = binding.basalAbsoluteInput.editTextId
        binding.durationLabel.labelFor = binding.duration.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        var percent = 0
        var absolute = 0.0
        val durationInMinutes = binding.duration.value.toInt()
        val profile = profileFunction.getProfile() ?: return false
        val actions: LinkedList<String> = LinkedList()
        if (isPercentPump) {
            val basalPercentInput = SafeParse.stringToInt(binding.basalPercentInput.text)
            percent = constraintChecker.applyBasalPercentConstraints(ConstraintObject(basalPercentInput, aapsLogger), profile).value()
            actions.add(rh.gs(app.aaps.core.ui.R.string.tempbasal_label) + ": $percent%")
            actions.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, durationInMinutes))
            if (percent != basalPercentInput) actions.add(rh.gs(app.aaps.core.ui.R.string.constraint_applied))
        } else {
            val basalAbsoluteInput = SafeParse.stringToDouble(binding.basalAbsoluteInput.text)
            absolute = constraintChecker.applyBasalConstraints(ConstraintObject(basalAbsoluteInput, aapsLogger), profile).value()
            actions.add(rh.gs(app.aaps.core.ui.R.string.tempbasal_label) + ": " + rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, absolute))
            actions.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, durationInMinutes))
            if (abs(absolute - basalAbsoluteInput) > 0.01)
                actions.add(rh.gs(app.aaps.core.ui.R.string.constraint_applied).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.tempbasal_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                val callback: Callback = object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.temp_basal_delivery_error), app.aaps.core.ui.R.raw.boluserror)
                        }
                    }
                }
                if (isPercentPump) {
                    uel.log(
                        action = Action.TEMP_BASAL, source = Sources.TempBasalDialog,
                        listValues = listOf(
                            ValueWithUnit.Percent(percent),
                            ValueWithUnit.Minute(durationInMinutes)
                        )
                    )
                    commandQueue.tempBasalPercent(percent, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
                } else {
                    uel.log(
                        action = Action.TEMP_BASAL, source = Sources.TempBasalDialog,
                        listValues = listOf(
                            ValueWithUnit.Insulin(absolute),
                            ValueWithUnit.Minute(durationInMinutes)
                        )
                    )
                    commandQueue.tempBasalAbsolute(absolute, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
                }
            })
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