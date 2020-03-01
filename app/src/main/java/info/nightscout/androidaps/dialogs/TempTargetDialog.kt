package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SP
import kotlinx.android.synthetic.main.dialog_temptarget.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*

class TempTargetDialog : DialogFragmentWithDate() {

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("overview_temptarget_duration", overview_temptarget_duration.value)
        savedInstanceState.putDouble("overview_temptarget_temptarget", overview_temptarget_temptarget.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_temptarget, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        overview_temptarget_duration.setParams(savedInstanceState?.getDouble("overview_temptarget_duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, ok)

        if (ProfileFunctions.getSystemUnits() == Constants.MMOL)
            overview_temptarget_temptarget.setParams(
                savedInstanceState?.getDouble("overview_temptarget_temptarget")
                    ?: Constants.MIN_TT_MMOL,
                Constants.MIN_TT_MMOL, Constants.MAX_TT_MMOL, 0.1, DecimalFormat("0.0"), false, ok)
        else
            overview_temptarget_temptarget.setParams(
                savedInstanceState?.getDouble("overview_temptarget_temptarget")
                    ?: Constants.MIN_TT_MGDL,
                Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1.0, DecimalFormat("0"), false, ok)

        val units = ProfileFunctions.getSystemUnits()
        overview_temptarget_units.text = if (units == Constants.MMOL) MainApp.gs(R.string.mmol) else MainApp.gs(R.string.mgdl)
        // temp target
        context?.let { context ->
            val reasonList: List<String> = Lists.newArrayList(
                MainApp.gs(R.string.manual),
                MainApp.gs(R.string.cancel),
                MainApp.gs(R.string.eatingsoon),
                MainApp.gs(R.string.activity),
                MainApp.gs(R.string.hypo)
            )
            val adapterReason = ArrayAdapter(context, R.layout.spinner_centered, reasonList)
            overview_temptarget_reason.adapter = adapterReason
            overview_temptarget_reason.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View, position: Int, id: Long) {
                    val defaultDuration: Double
                    val defaultTarget: Double
                    when (reasonList[position]) {
                        MainApp.gs(R.string.eatingsoon) -> {
                            defaultDuration = DefaultValueHelper.determineEatingSoonTTDuration().toDouble()
                            defaultTarget = DefaultValueHelper.determineEatingSoonTT()
                        }

                        MainApp.gs(R.string.activity)   -> {
                            defaultDuration = DefaultValueHelper.determineActivityTTDuration().toDouble()
                            defaultTarget = DefaultValueHelper.determineActivityTT()
                        }

                        MainApp.gs(R.string.hypo)       -> {
                            defaultDuration = DefaultValueHelper.determineHypoTTDuration().toDouble()
                            defaultTarget = DefaultValueHelper.determineHypoTT()
                        }

                        MainApp.gs(R.string.cancel)     -> {
                            defaultDuration = 0.0
                            defaultTarget = 0.0
                        }

                        else                            -> {
                            defaultDuration = overview_temptarget_duration.value
                            defaultTarget = overview_temptarget_temptarget.value
                        }
                    }
                    overview_temptarget_temptarget.value = defaultTarget
                    overview_temptarget_duration.value = defaultDuration
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
    }

    override fun submit(): Boolean {
        val actions: LinkedList<String> = LinkedList()
        val reason = overview_temptarget_reason.selectedItem.toString()
        val unitResId = if (ProfileFunctions.getSystemUnits() == Constants.MGDL) R.string.mgdl else R.string.mmol
        val target = overview_temptarget_temptarget.value
        val duration = overview_temptarget_duration.value.toInt()
        if (target != 0.0 && duration != 0) {
            actions.add(MainApp.gs(R.string.reason) + ": " + reason)
            actions.add(MainApp.gs(R.string.nsprofileview_target_label) + ": " + Profile.toCurrentUnitsString(target) + " " + MainApp.gs(unitResId))
            actions.add(MainApp.gs(R.string.duration) + ": " + MainApp.gs(R.string.format_mins, duration))
        } else {
            actions.add(MainApp.gs(R.string.stoptemptarget))
        }
        if (eventTimeChanged)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, MainApp.gs(R.string.careportal_temporarytarget), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                log.debug("USER ENTRY: TEMP TARGET $target duration: $duration")
                if (target == 0.0 || duration == 0) {
                    val tempTarget = TempTarget()
                        .date(eventTime)
                        .duration(0)
                        .low(0.0).high(0.0)
                        .source(Source.USER)
                    TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                } else {
                    val tempTarget = TempTarget()
                        .date(eventTime)
                        .duration(duration.toInt())
                        .reason(reason)
                        .source(Source.USER)
                        .low(Profile.toMgdl(target, ProfileFunctions.getSystemUnits()))
                        .high(Profile.toMgdl(target, ProfileFunctions.getSystemUnits()))
                    TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                }
                if (duration == 10) SP.putBoolean(R.string.key_objectiveusetemptarget, true)
            })
        }
        return true
    }
}
