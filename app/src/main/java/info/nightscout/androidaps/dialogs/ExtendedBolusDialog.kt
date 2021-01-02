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
import kotlinx.android.synthetic.main.dialog_extendedbolus.*
import kotlinx.android.synthetic.main.okcancel.*
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

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("actions_extendedbolus_insulin", actions_extendedbolus_insulin.value)
        savedInstanceState.putDouble("actions_extendedbolus_duration", actions_extendedbolus_duration.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_extendedbolus, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pumpDescription = activePlugin.activePump.pumpDescription

        val maxInsulin = constraintChecker.getMaxExtendedBolusAllowed().value()
        val extendedStep = pumpDescription.extendedBolusStep
        actions_extendedbolus_insulin.setParams(savedInstanceState?.getDouble("actions_extendedbolus_insulin")
            ?: extendedStep, extendedStep, maxInsulin, extendedStep, DecimalFormat("0.00"), false, ok)

        val extendedDurationStep = pumpDescription.extendedBolusDurationStep
        val extendedMaxDuration = pumpDescription.extendedBolusMaxDuration
        actions_extendedbolus_duration.setParams(savedInstanceState?.getDouble("actions_extendedbolus_duration")
            ?: extendedDurationStep, extendedDurationStep, extendedMaxDuration, extendedDurationStep, DecimalFormat("0"), false, ok)
    }

    override fun submit(): Boolean {
        val insulin = SafeParse.stringToDouble(actions_extendedbolus_insulin?.text ?: return false)
        val durationInMinutes = actions_extendedbolus_duration.value.toInt()
        val actions: LinkedList<String> = LinkedList()
        val insulinAfterConstraint = constraintChecker.applyExtendedBolusConstraints(Constraint(insulin)).value()
        actions.add(resourceHelper.gs(R.string.formatinsulinunits, insulinAfterConstraint))
        actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, durationInMinutes))
        if (abs(insulinAfterConstraint - insulin) > 0.01)
            actions.add(resourceHelper.gs(R.string.constraintapllied).formatColor(resourceHelper, R.color.warning))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.extended_bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
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