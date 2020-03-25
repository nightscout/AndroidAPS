package info.nightscout.androidaps.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SafeParse
import kotlinx.android.synthetic.main.dialog_extendedbolus.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

class ExtendedBolusDialog : DialogFragmentWithDate() {

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

        val pumpDescription = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription ?: return

        val maxInsulin = MainApp.getConstraintChecker().maxExtendedBolusAllowed.value()
        val extendedStep = pumpDescription.extendedBolusStep
        actions_extendedbolus_insulin.setParams(savedInstanceState?.getDouble("actions_extendedbolus_insulin")
            ?: extendedStep, extendedStep, maxInsulin, extendedStep, DecimalFormat("0.00"), false, ok)

        val extendedDurationStep = pumpDescription.extendedBolusDurationStep
        val extendedMaxDuration = pumpDescription.extendedBolusMaxDuration
        actions_extendedbolus_duration.setParams(savedInstanceState?.getDouble("actions_extendedbolus_duration")
            ?: extendedDurationStep, extendedDurationStep, extendedMaxDuration, extendedDurationStep, DecimalFormat("0"), false, ok)
    }

    override fun submit(): Boolean {
        val insulin = SafeParse.stringToDouble(actions_extendedbolus_insulin.text)
        val durationInMinutes = SafeParse.stringToInt(actions_extendedbolus_duration.text)
        val actions: LinkedList<String> = LinkedList()
        val insulinAfterConstraint = MainApp.getConstraintChecker().applyExtendedBolusConstraints(Constraint(insulin)).value()
        actions.add(MainApp.gs(R.string.formatinsulinunits, insulinAfterConstraint))
        actions.add(MainApp.gs(R.string.duration) + ": " + MainApp.gs(R.string.format_mins, durationInMinutes))
        if (abs(insulinAfterConstraint - insulin) > 0.01)
            actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.constraintapllied) + "</font>")

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, MainApp.gs(R.string.extended_bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                log.debug("USER ENTRY: EXTENDED BOLUS $insulinAfterConstraint duration: $durationInMinutes")
                ConfigBuilderPlugin.getPlugin().commandQueue.extendedBolus(insulinAfterConstraint, durationInMinutes, object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(MainApp.instance(), ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", MainApp.gs(R.string.treatmentdeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            MainApp.instance().startActivity(i)
                        }
                    }
                })
            }, null)
        }
        return true
    }
}