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
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_temptarget.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class TempTargetDialog : DialogFragmentWithDate() {
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider

    lateinit var reasonList: List<String>

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

        if (profileFunction.getUnits() == Constants.MMOL)
            overview_temptarget_temptarget.setParams(
                savedInstanceState?.getDouble("overview_temptarget_temptarget")
                    ?: Constants.MIN_TT_MMOL,
                Constants.MIN_TT_MMOL, Constants.MAX_TT_MMOL, 0.1, DecimalFormat("0.0"), false, ok)
        else
            overview_temptarget_temptarget.setParams(
                savedInstanceState?.getDouble("overview_temptarget_temptarget")
                    ?: Constants.MIN_TT_MGDL,
                Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1.0, DecimalFormat("0"), false, ok)

        val units = profileFunction.getUnits()
        overview_temptarget_units.text = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)

        // temp target
        context?.let { context ->
            if (activePlugin.activeTreatments.tempTargetFromHistory != null)
                overview_temptarget_cancel?.visibility = View.VISIBLE
            else
                overview_temptarget_cancel?.visibility = View.GONE

            reasonList = Lists.newArrayList(
                resourceHelper.gs(R.string.manual),
                resourceHelper.gs(R.string.eatingsoon),
                resourceHelper.gs(R.string.activity),
                resourceHelper.gs(R.string.hypo)
            )
            val adapterReason = ArrayAdapter(context, R.layout.spinner_centered, reasonList)
            overview_temptarget_reason.adapter = adapterReason

            overview_temptarget_cancel?.setOnClickListener { shortClick(it) }
            overview_temptarget_eating_soon?.setOnClickListener { shortClick(it) }
            overview_temptarget_activity?.setOnClickListener { shortClick(it) }
            overview_temptarget_hypo?.setOnClickListener { shortClick(it) }

            overview_temptarget_eating_soon?.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
            overview_temptarget_activity?.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
            overview_temptarget_hypo?.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
        }
    }

    private fun shortClick(v:View){
        v.performLongClick()
        if (submit()) dismiss()
    }

    private fun longClick(v:View) {
        when (v.id) {
            R.id.overview_temptarget_eating_soon    -> {
                overview_temptarget_temptarget.value = defaultValueHelper.determineEatingSoonTT()
                overview_temptarget_duration.value = defaultValueHelper.determineEatingSoonTTDuration().toDouble()
                overview_temptarget_reason.setSelection(reasonList.indexOf( resourceHelper.gs(R.string.eatingsoon)))
            }
            R.id.overview_temptarget_activity       -> {
                overview_temptarget_temptarget.value = defaultValueHelper.determineActivityTT()
                overview_temptarget_duration.value = defaultValueHelper.determineActivityTTDuration().toDouble()
                overview_temptarget_reason.setSelection(reasonList.indexOf(resourceHelper.gs(R.string.activity)))
            }
            R.id.overview_temptarget_hypo           -> {
                overview_temptarget_temptarget.value = defaultValueHelper.determineHypoTT()
                overview_temptarget_duration.value = defaultValueHelper.determineHypoTTDuration().toDouble()
                overview_temptarget_reason.setSelection(reasonList.indexOf(resourceHelper.gs(R.string.hypo)))
            }
        }
    }

    override fun submit(): Boolean {
        val actions: LinkedList<String> = LinkedList()
        val reason = overview_temptarget_reason?.selectedItem?.toString() ?: return false
        val unitResId = if (profileFunction.getUnits() == Constants.MGDL) R.string.mgdl else R.string.mmol
        val target = overview_temptarget_temptarget.value
        val duration = overview_temptarget_duration.value.toInt()
        if (target != 0.0 && duration != 0) {
            actions.add(resourceHelper.gs(R.string.reason) + ": " + reason)
            actions.add(resourceHelper.gs(R.string.target_label) + ": " + Profile.toCurrentUnitsString(profileFunction, target) + " " + resourceHelper.gs(unitResId))
            actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, duration))
        } else {
            actions.add(resourceHelper.gs(R.string.stoptemptarget))
        }
        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_temporarytarget), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                aapsLogger.debug("USER ENTRY: TEMP TARGET $target duration: $duration")
                if (target == 0.0 || duration == 0) {
                    val tempTarget = TempTarget()
                        .date(eventTime)
                        .duration(0)
                        .low(0.0).high(0.0)
                        .source(Source.USER)
                    treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                } else {
                    val tempTarget = TempTarget()
                        .date(eventTime)
                        .duration(duration)
                        .reason(reason)
                        .source(Source.USER)
                        .low(Profile.toMgdl(target, profileFunction.getUnits()))
                        .high(Profile.toMgdl(target, profileFunction.getUnits()))
                    treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                }
                if (duration == 10) sp.putBoolean(R.string.key_objectiveusetemptarget, true)
            })
        }
        return true
    }
}
