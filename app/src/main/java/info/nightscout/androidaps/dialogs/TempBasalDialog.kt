package info.nightscout.androidaps.dialogs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.databinding.DialogTempbasalBinding
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class TempBasalDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var ctx: Context

    private var isPercentPump = true

    private var _binding: DialogTempbasalBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putDouble("basalpercentinput", binding.basalpercentinput.value)
        savedInstanceState.putDouble("basalabsoluteinput", binding.basalabsoluteinput.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
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

        binding.basalpercentinput.setParams(savedInstanceState?.getDouble("basalpercentinput")
            ?: 100.0, 0.0, maxTempPercent, tempPercentStep, DecimalFormat("0"), true, binding.okcancel.ok)

        binding.basalabsoluteinput.setParams(savedInstanceState?.getDouble("basalabsoluteinput")
            ?: profile.basal, 0.0, pumpDescription.maxTempAbsolute, pumpDescription.tempAbsoluteStep, DecimalFormat("0.00"), true, binding.okcancel.ok)

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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        var percent = 0
        var absolute = 0.0
        val durationInMinutes = binding.duration.value?.toInt() ?: return false
        val profile = profileFunction.getProfile() ?: return false
        val actions: LinkedList<String> = LinkedList()
        if (isPercentPump) {
            val basalPercentInput = SafeParse.stringToInt(binding.basalpercentinput.text)
            percent = constraintChecker.applyBasalPercentConstraints(Constraint(basalPercentInput), profile).value()
            actions.add(resourceHelper.gs(R.string.tempbasal_label) + ": $percent%")
            actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, durationInMinutes))
            if (percent != basalPercentInput) actions.add(resourceHelper.gs(R.string.constraintapllied))
        } else {
            val basalAbsoluteInput = SafeParse.stringToDouble(binding.basalabsoluteinput.text)
            absolute = constraintChecker.applyBasalConstraints(Constraint(basalAbsoluteInput), profile).value()
            actions.add(resourceHelper.gs(R.string.tempbasal_label) + ": " + resourceHelper.gs(R.string.pump_basebasalrate, absolute))
            actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, durationInMinutes))
            if (abs(absolute - basalAbsoluteInput) > 0.01)
                actions.add(resourceHelper.gs(R.string.constraintapllied).formatColor(resourceHelper, R.color.warning))
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.tempbasal_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                val callback: Callback = object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(ctx, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.tempbasaldeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                    }
                }
                if (isPercentPump) {
                    aapsLogger.debug("USER ENTRY: TEMP BASAL $percent% duration: $durationInMinutes")
                    commandQueue.tempBasalPercent(percent, durationInMinutes, true, profile, callback)
                } else {
                    aapsLogger.debug("USER ENTRY: TEMP BASAL $absolute duration: $durationInMinutes")
                    commandQueue.tempBasalAbsolute(absolute, durationInMinutes, true, profile, callback)
                }
            })
        }
        return true
    }
}