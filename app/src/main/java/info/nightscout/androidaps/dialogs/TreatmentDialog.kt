package info.nightscout.androidaps.dialogs

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.OKDialog
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.ToastUtils
import kotlinx.android.synthetic.main.dialog_treatment.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs

class TreatmentDialog : DialogFragmentWithDate() {
    private var maxCarbs = MainApp.getConstraintChecker().maxCarbsAllowed.value().toDouble()
    private var maxInsulin = MainApp.getConstraintChecker().maxBolusAllowed.value()

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            validateInputs()
        }
    }

    private fun validateInputs() {
        if (SafeParse.stringToInt(overview_treatment_carbs.text) > maxCarbs) {
            overview_treatment_carbs.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.carbsconstraintapplied))
        }
        if (SafeParse.stringToDouble(overview_treatment_insulin.text) > maxInsulin) {
            overview_treatment_insulin.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.bolusconstraintapplied))
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("overview_treatment_carbs", overview_treatment_carbs.value)
        savedInstanceState.putDouble("overview_treatment_insulin", overview_treatment_insulin.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_treatment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val pumpDescription = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription ?: return
        overview_treatment_carbs.setParams(savedInstanceState?.getDouble("overview_treatment_carbs")
            ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, ok, textWatcher)
        overview_treatment_insulin.setParams(savedInstanceState?.getDouble("overview_treatment_insulin")
            ?: 0.0, 0.0, maxInsulin, pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(), false, ok, textWatcher)
    }

    override fun submit(): Boolean {
        val pumpDescription = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription
            ?: return false
        val insulin = SafeParse.stringToDouble(overview_treatment_insulin.text)
        val carbs = SafeParse.stringToInt(overview_treatment_carbs.text)
        val recordOnlyChecked = overview_treatment_record_only.isChecked
        val actions: LinkedList<String?> = LinkedList()
        val insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(Constraint(insulin)).value()
        val carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(Constraint(carbs)).value()

        if (insulinAfterConstraints > 0) {
            actions.add(MainApp.gs(R.string.bolus) + ": " + "<font color='" + MainApp.gc(R.color.bolus) + "'>" + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints) + MainApp.gs(R.string.insulin_unit_shortname) + "</font>")
            if (recordOnlyChecked)
                actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.bolusrecordedonly) + "</font>")
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(MainApp.gs(R.string.bolusconstraintappliedwarning, MainApp.gc(R.color.warning), insulin, insulinAfterConstraints))
        }
        if (carbsAfterConstraints > 0) {
            actions.add(MainApp.gs(R.string.carbs) + ": " + "<font color='" + MainApp.gc(R.color.carbs) + "'>" + MainApp.gs(R.string.format_carbs, carbsAfterConstraints) + "</font>")
            if (carbsAfterConstraints != carbs)
                actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.carbsconstraintapplied) + "</font>")
        }
        if (insulinAfterConstraints > 0 || carbsAfterConstraints > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, MainApp.gs(R.string.overview_treatment_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    log.debug("USER ENTRY: BOLUS insulin $insulin carbs: $carbs")
                    val detailedBolusInfo = DetailedBolusInfo()
                    if (insulinAfterConstraints == 0.0) detailedBolusInfo.eventType = CareportalEvent.CARBCORRECTION
                    if (carbsAfterConstraints == 0) detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS
                    detailedBolusInfo.insulin = insulinAfterConstraints
                    detailedBolusInfo.carbs = carbsAfterConstraints.toDouble()
                    detailedBolusInfo.context = context
                    detailedBolusInfo.source = Source.USER
                    if (!(recordOnlyChecked && (detailedBolusInfo.insulin > 0 || pumpDescription.storesCarbInfo))) {
                        ConfigBuilderPlugin.getPlugin().commandQueue.bolus(detailedBolusInfo, object : Callback() {
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
                    } else
                        TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false)
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, MainApp.gs(R.string.overview_treatment_label), MainApp.gs(R.string.no_action_selected))
            }
        return true
    }
}