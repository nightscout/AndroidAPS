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
import info.nightscout.androidaps.databinding.DialogExtendedbolusBinding
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
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

class ExtendedBolusDialog : DialogFragmentWithDate() {

    @Inject lateinit var ctx: Context
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider

    private var _binding: DialogExtendedbolusBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("insulin", binding.insulin.value)
        savedInstanceState.putDouble("duration", binding.duration.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogExtendedbolusBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pumpDescription = activePlugin.activePump.pumpDescription

        val maxInsulin = constraintChecker.getMaxExtendedBolusAllowed().value()
        val extendedStep = pumpDescription.extendedBolusStep
        binding.insulin.setParams(savedInstanceState?.getDouble("insulin")
            ?: extendedStep, extendedStep, maxInsulin, extendedStep, DecimalFormat("0.00"), false, binding.okcancel.ok)

        val extendedDurationStep = pumpDescription.extendedBolusDurationStep
        val extendedMaxDuration = pumpDescription.extendedBolusMaxDuration
        binding.duration.setParams(savedInstanceState?.getDouble("duration")
            ?: extendedDurationStep, extendedDurationStep, extendedMaxDuration, extendedDurationStep, DecimalFormat("0"), false, binding.okcancel.ok)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val insulin = SafeParse.stringToDouble(binding.insulin.text ?: return false)
        val durationInMinutes = binding.duration.value.toInt()
        val actions: LinkedList<String> = LinkedList()
        val insulinAfterConstraint = constraintChecker.applyExtendedBolusConstraints(Constraint(insulin)).value()
        actions.add(resourceHelper.gs(R.string.formatinsulinunits, insulinAfterConstraint))
        actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, durationInMinutes))
        if (abs(insulinAfterConstraint - insulin) > 0.01)
            actions.add(resourceHelper.gs(R.string.constraintapllied).formatColor(resourceHelper, R.color.warning))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.extended_bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                aapsLogger.debug("USER ENTRY: EXTENDED BOLUS $insulinAfterConstraint duration: $durationInMinutes")
                commandQueue.extendedBolus(insulinAfterConstraint, durationInMinutes, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(ctx, ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", resourceHelper.gs(R.string.treatmentdeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            ctx.startActivity(i)
                        }
                    }
                })
            }, null)
        }
        return true
    }
}