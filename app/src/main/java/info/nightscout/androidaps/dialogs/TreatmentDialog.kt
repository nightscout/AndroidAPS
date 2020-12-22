package info.nightscout.androidaps.dialogs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_insulin.*
import kotlinx.android.synthetic.main.dialog_treatment.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class TreatmentDialog : DialogFragmentWithDate() {
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var ctx: Context
    @Inject lateinit var config: Config

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {}
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            validateInputs()
        }
    }

    private fun validateInputs() {
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (SafeParse.stringToInt(overview_treatment_carbs.text) > maxCarbs) {
            overview_treatment_carbs.value = 0.0
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.carbsconstraintapplied))
        }
        if (SafeParse.stringToDouble(overview_treatment_insulin.text) > maxInsulin) {
            overview_treatment_insulin.value = 0.0
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.bolusconstraintapplied))
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

        if (config.NSCLIENT) {
            overview_treatment_record_only.isChecked = true
            overview_treatment_record_only.isEnabled = false
        }
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val pumpDescription = activePlugin.activePump.pumpDescription
        overview_treatment_carbs.setParams(savedInstanceState?.getDouble("overview_treatment_carbs")
            ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, ok, textWatcher)
        overview_treatment_insulin.setParams(savedInstanceState?.getDouble("overview_treatment_insulin")
            ?: 0.0, 0.0, maxInsulin, pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, ok, textWatcher)
    }

    override fun submit(): Boolean {
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = SafeParse.stringToDouble(overview_treatment_insulin?.text ?: return false)
        val carbs = SafeParse.stringToInt(overview_treatment_carbs.text)
        val recordOnlyChecked = overview_treatment_record_only.isChecked
        val actions: LinkedList<String?> = LinkedList()
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()

        if (insulinAfterConstraints > 0) {
            actions.add(resourceHelper.gs(R.string.bolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, resourceHelper).formatColor(resourceHelper, R.color.bolus))
            if (recordOnlyChecked)
                actions.add(resourceHelper.gs(R.string.bolusrecordedonly).formatColor(resourceHelper, R.color.warning))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(resourceHelper.gs(R.string.bolusconstraintappliedwarn, insulin, insulinAfterConstraints).formatColor(resourceHelper, R.color.warning))
        }
        if (carbsAfterConstraints > 0) {
            actions.add(resourceHelper.gs(R.string.carbs) + ": " + resourceHelper.gs(R.string.format_carbs, carbsAfterConstraints).formatColor(resourceHelper, R.color.carbs))
            if (carbsAfterConstraints != carbs)
                actions.add(resourceHelper.gs(R.string.carbsconstraintapplied).formatColor(resourceHelper, R.color.warning))
        }
        if (insulinAfterConstraints > 0 || carbsAfterConstraints > 0) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.overview_treatment_label), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    aapsLogger.debug("USER ENTRY: BOLUS insulin $insulin carbs: $carbs")
                    val detailedBolusInfo = DetailedBolusInfo()
                    if (insulinAfterConstraints == 0.0) detailedBolusInfo.eventType = CareportalEvent.CARBCORRECTION
                    if (carbsAfterConstraints == 0) detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS
                    detailedBolusInfo.insulin = insulinAfterConstraints
                    detailedBolusInfo.carbs = carbsAfterConstraints.toDouble()
                    detailedBolusInfo.context = context
                    detailedBolusInfo.source = Source.USER
                    if (!(recordOnlyChecked && (detailedBolusInfo.insulin > 0 || pumpDescription.storesCarbInfo))) {
                        commandQueue.bolus(detailedBolusInfo, object : Callback() {
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
                    } else
                        activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, resourceHelper.gs(R.string.overview_treatment_label), resourceHelper.gs(R.string.no_action_selected))
            }
        return true
    }
}