package info.nightscout.androidaps.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.databinding.DialogTempbasalBinding
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.shared.SafeParse
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.extensions.formatColor
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.protection.ProtectionCheck
import info.nightscout.androidaps.utils.protection.ProtectionCheck.Protection.BOLUS
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.logging.LTag
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class TempBasalDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var ctx: Context
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck

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

        binding.basalPercentInput.setParams(savedInstanceState?.getDouble("basalPercentInput")
            ?: 100.0, 0.0, maxTempPercent, tempPercentStep, DecimalFormat("0"), true, binding.okcancel.ok)

        binding.basalAbsoluteInput.setParams(savedInstanceState?.getDouble("basalAbsoluteInput")
            ?: profile.getBasal(), 0.0, pumpDescription.maxTempAbsolute, pumpDescription.tempAbsoluteStep, DecimalFormat("0.00"), true, binding.okcancel.ok)

        val tempDurationStep = pumpDescription.tempDurationStep.toDouble()
        val tempMaxDuration = pumpDescription.tempMaxDuration.toDouble()
        binding.duration.setParams(savedInstanceState?.getDouble("duration")
            ?: tempDurationStep, tempDurationStep, tempMaxDuration, tempDurationStep, DecimalFormat("0"), false, binding.okcancel.ok)

        isPercentPump = pumpDescription.tempBasalStyle and PumpDescription.PERCENT == PumpDescription.PERCENT
        if (isPercentPump) {
            binding.percentLayout.visibility = View.VISIBLE
            binding.absoluteLayout.visibility = View.GONE
        } else {
            binding.percentLayout.visibility = View.GONE
            binding.absoluteLayout.visibility = View.VISIBLE
        }
        binding.basalPercentInput.editText?.id?.let { binding.basalPercentLabel.labelFor = it }
        binding.basalAbsoluteInput.editText?.id?.let { binding.basalAbsoluteLabel.labelFor = it }
        binding.duration.editText?.id?.let { binding.durationLabel.labelFor = it }
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
            percent = constraintChecker.applyBasalPercentConstraints(Constraint(basalPercentInput), profile).value()
            actions.add(rh.gs(R.string.tempbasal_label) + ": $percent%")
            actions.add(rh.gs(R.string.duration) + ": " + rh.gs(R.string.format_mins, durationInMinutes))
            if (percent != basalPercentInput) actions.add(rh.gs(R.string.constraintapllied))
        } else {
            val basalAbsoluteInput = SafeParse.stringToDouble(binding.basalAbsoluteInput.text)
            absolute = constraintChecker.applyBasalConstraints(Constraint(basalAbsoluteInput), profile).value()
            actions.add(rh.gs(R.string.tempbasal_label) + ": " + rh.gs(R.string.pump_basebasalrate, absolute))
            actions.add(rh.gs(R.string.duration) + ": " + rh.gs(R.string.format_mins, durationInMinutes))
            if (abs(absolute - basalAbsoluteInput) > 0.01)
                actions.add(rh.gs(R.string.constraintapllied).formatColor(context, rh, R.attr.warningColor))
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.tempbasal_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                val callback: Callback = object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            ErrorHelperActivity.runAlarm(ctx, result.comment, rh.gs(R.string.tempbasaldeliveryerror), R.raw.boluserror)
                        }
                    }
                }
                if (isPercentPump) {
                    uel.log(Action.TEMP_BASAL, Sources.TempBasalDialog,
                        ValueWithUnit.Percent(percent),
                        ValueWithUnit.Minute(durationInMinutes))
                    commandQueue.tempBasalPercent(percent, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
                } else {
                    uel.log(Action.TEMP_BASAL, Sources.TempBasalDialog,
                        ValueWithUnit.Insulin(absolute),
                        ValueWithUnit.Minute(durationInMinutes))
                    commandQueue.tempBasalAbsolute(absolute, durationInMinutes, true, profile, PumpSync.TemporaryBasalType.NORMAL, callback)
                }
            })
        }
        return true
    }

    override fun onResume() {
        super.onResume()
        if(!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.name}")
                    ToastUtils.showToastInUiThread(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}
