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
import info.nightscout.androidaps.interfaces.PumpDescription
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SafeParse
import kotlinx.android.synthetic.main.dialog_tempbasal.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

class TempBasalDialog : DialogFragmentWithDate() {
    private var isPercentPump = true

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("actions_tempbasal_duration", actions_tempbasal_duration.value)
        savedInstanceState.putDouble("actions_tempbasal_basalpercentinput", actions_tempbasal_basalpercentinput.value)
        savedInstanceState.putDouble("actions_tempbasal_basalabsoluteinput", actions_tempbasal_basalabsoluteinput.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_tempbasal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pumpDescription = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription ?: return
        val profile = ProfileFunctions.getInstance().profile ?: return

        val maxTempPercent = pumpDescription.maxTempPercent.toDouble()
        val tempPercentStep = pumpDescription.tempPercentStep.toDouble()

        actions_tempbasal_basalpercentinput.setParams(savedInstanceState?.getDouble("actions_tempbasal_basalpercentinput")
            ?: 100.0, 0.0, maxTempPercent, tempPercentStep, DecimalFormat("0"), true, ok)

        actions_tempbasal_basalabsoluteinput.setParams(savedInstanceState?.getDouble("actions_tempbasal_basalabsoluteinput")
            ?: profile.basal, 0.0, pumpDescription.maxTempAbsolute, pumpDescription.tempAbsoluteStep, DecimalFormat("0.00"), true, ok)

        val tempDurationStep = pumpDescription.tempDurationStep.toDouble()
        val tempMaxDuration = pumpDescription.tempMaxDuration.toDouble()
        actions_tempbasal_duration.setParams(savedInstanceState?.getDouble("actions_tempbasal_duration")
            ?: tempDurationStep, tempDurationStep, tempMaxDuration, tempDurationStep, DecimalFormat("0"), false, ok)

        isPercentPump = pumpDescription.tempBasalStyle and PumpDescription.PERCENT == PumpDescription.PERCENT
        if (isPercentPump) {
            actions_tempbasal_percent_layout.visibility = View.VISIBLE
            actions_tempbasal_absolute_layout.visibility = View.GONE
        } else {
            actions_tempbasal_percent_layout.visibility = View.GONE
            actions_tempbasal_absolute_layout.visibility = View.VISIBLE
        }
    }

    override fun submit(): Boolean {
        var percent = 0
        var absolute = 0.0
        val durationInMinutes = SafeParse.stringToInt(actions_tempbasal_duration.text)
        val profile = ProfileFunctions.getInstance().profile ?: return false
        val actions: LinkedList<String> = LinkedList()
        if (isPercentPump) {
            val basalPercentInput = SafeParse.stringToInt(actions_tempbasal_basalpercentinput.text)
            percent = MainApp.getConstraintChecker().applyBasalPercentConstraints(Constraint(basalPercentInput), profile).value()
            actions.add(MainApp.gs(R.string.pump_tempbasal_label)+ ": $percent%")
            actions.add(MainApp.gs(R.string.duration) + ": " + MainApp.gs(R.string.format_mins, durationInMinutes))
            if (percent != basalPercentInput) actions.add(MainApp.gs(R.string.constraintapllied))
        } else {
            val basalAbsoluteInput = SafeParse.stringToDouble(actions_tempbasal_basalabsoluteinput.text)
            absolute = MainApp.getConstraintChecker().applyBasalConstraints(Constraint(basalAbsoluteInput), profile).value()
            actions.add(MainApp.gs(R.string.pump_tempbasal_label)+ ": " + MainApp.gs(R.string.pump_basebasalrate, absolute))
            actions.add(MainApp.gs(R.string.duration) + ": " + MainApp.gs(R.string.format_mins, durationInMinutes))
            if (abs(absolute - basalAbsoluteInput) > 0.01)
                actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.constraintapllied) + "</font>")
        }
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, MainApp.gs(R.string.pump_tempbasal_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                val callback: Callback = object : Callback() {
                    override fun run() {
                        if (!result.success) {
                            val i = Intent(MainApp.instance(), ErrorHelperActivity::class.java)
                            i.putExtra("soundid", R.raw.boluserror)
                            i.putExtra("status", result.comment)
                            i.putExtra("title", MainApp.gs(R.string.tempbasaldeliveryerror))
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            MainApp.instance().startActivity(i)
                        }
                    }
                }
                if (isPercentPump) {
                    log.debug("USER ENTRY: TEMP BASAL $percent% duration: $durationInMinutes")
                    ConfigBuilderPlugin.getPlugin().commandQueue.tempBasalPercent(percent, durationInMinutes, true, profile, callback)
                } else {
                    log.debug("USER ENTRY: TEMP BASAL $absolute duration: $durationInMinutes")
                    ConfigBuilderPlugin.getPlugin().commandQueue.tempBasalAbsolute(absolute, durationInMinutes, true, profile, callback)
                }
            })
        }
        return true
    }
}