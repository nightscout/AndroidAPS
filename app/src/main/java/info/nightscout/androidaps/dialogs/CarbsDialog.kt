package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.ToastUtils
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_carbs.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.max

class CarbsDialog : DialogFragmentWithDate() {
    @Inject lateinit var mainApp: MainApp
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var iobCobCalculatorPlugin: IobCobCalculatorPlugin
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var carbsGenerator: CarbsGenerator

    companion object {
        private const val FAV1_DEFAULT = 5
        private const val FAV2_DEFAULT = 10
        private const val FAV3_DEFAULT = 20
    }

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            validateInputs()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs() {
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val time = overview_carbs_time.value.toInt()
        if (time > 12 * 60 || time < -12 * 60) {
            overview_carbs_time.value = 0.0
            ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.constraintapllied))
        }
        if (overview_carbs_duration.value > 10) {
            overview_carbs_duration.value = 0.0
            ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.constraintapllied))
        }
        if (overview_carbs_carbs.value.toInt() > maxCarbs) {
            overview_carbs_carbs.value = 0.0
            ToastUtils.showToastInUiThread(mainApp, resourceHelper.gs(R.string.carbsconstraintapplied))
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("overview_carbs_time", overview_carbs_time.value)
        savedInstanceState.putDouble("overview_carbs_duration", overview_carbs_duration.value)
        savedInstanceState.putDouble("overview_carbs_carbs", overview_carbs_carbs.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_carbs, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        overview_carbs_time.setParams(savedInstanceState?.getDouble("overview_carbs_time")
            ?: 0.0, -12 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, ok, textWatcher)

        overview_carbs_duration.setParams(savedInstanceState?.getDouble("overview_carbs_duration")
            ?: 0.0, 0.0, 10.0, 1.0, DecimalFormat("0"), false, ok, textWatcher)

        overview_carbs_carbs.setParams(savedInstanceState?.getDouble("overview_carbs_carbs")
            ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, ok, textWatcher)

        overview_carbs_plus1.text = toSignedString(sp.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT))
        overview_carbs_plus1.setOnClickListener {
            overview_carbs_carbs.value = max(0.0, overview_carbs_carbs.value
                + sp.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT))
            validateInputs()
        }

        overview_carbs_plus2.text = toSignedString(sp.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT))
        overview_carbs_plus2.setOnClickListener {
            overview_carbs_carbs.value = max(0.0, overview_carbs_carbs.value
                + sp.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT))
            validateInputs()
        }

        overview_carbs_plus3.text = toSignedString(sp.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT))
        overview_carbs_plus3.setOnClickListener {
            overview_carbs_carbs.value = max(0.0, overview_carbs_carbs.value
                + sp.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT))
            validateInputs()
        }

        iobCobCalculatorPlugin.actualBg()?.let { bgReading ->
            if (bgReading.value < 72)
                overview_carbs_hypo_tt.isChecked = true
        }
        overview_carbs_hypo_tt.setOnClickListener {
            overview_carbs_activity_tt.isChecked = false
            overview_carbs_eating_soon_tt.isChecked = false
        }
        overview_carbs_activity_tt.setOnClickListener {
            overview_carbs_hypo_tt.isChecked = false
            overview_carbs_eating_soon_tt.isChecked = false
        }
        overview_carbs_eating_soon_tt.setOnClickListener {
            overview_carbs_hypo_tt.isChecked = false
            overview_carbs_activity_tt.isChecked = false
        }
    }

    private fun toSignedString(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
    }

    override fun submit(): Boolean {
        val carbs = overview_carbs_carbs?.value?.toInt() ?: return false
        val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(Constraint(carbs)).value()
        val units = profileFunction.getUnits()
        val activityTTDuration = defaultValueHelper.determineActivityTTDuration()
        val activityTT = defaultValueHelper.determineActivityTT()
        val eatingSoonTTDuration = defaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = defaultValueHelper.determineEatingSoonTT()
        val hypoTTDuration = defaultValueHelper.determineHypoTTDuration()
        val hypoTT = defaultValueHelper.determineHypoTT()
        val actions: LinkedList<String?> = LinkedList()
        val unitLabel = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)

        val activitySelected = overview_carbs_activity_tt.isChecked
        if (activitySelected)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + (DecimalFormatter.to1Decimal(activityTT) + " " + unitLabel + " (" + resourceHelper.gs(R.string.format_mins, activityTTDuration) + ")").formatColor(resourceHelper, R.color.tempTargetConfirmation))
        val eatingSoonSelected = overview_carbs_eating_soon_tt.isChecked
        if (eatingSoonSelected)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + (DecimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + resourceHelper.gs(R.string.format_mins, eatingSoonTTDuration) + ")").formatColor(resourceHelper, R.color.tempTargetConfirmation))
        val hypoSelected = overview_carbs_hypo_tt.isChecked
        if (hypoSelected)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + (DecimalFormatter.to1Decimal(hypoTT) + " " + unitLabel + " (" + resourceHelper.gs(R.string.format_mins, hypoTTDuration) + ")").formatColor(resourceHelper, R.color.tempTargetConfirmation))

        val timeOffset = overview_carbs_time.value.toInt()
        eventTime -= eventTime % 1000
        val time = eventTime + timeOffset * 1000 * 60
        if (timeOffset != 0)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(time))
        val duration = overview_carbs_duration.value.toInt()
        if (duration > 0)
            actions.add(resourceHelper.gs(R.string.duration) + ": " + duration + resourceHelper.gs(R.string.shorthour))
        if (carbsAfterConstraints > 0) {
            actions.add(resourceHelper.gs(R.string.carbs) + ": " + "<font color='" + resourceHelper.gc(R.color.carbs) + "'>" + resourceHelper.gs(R.string.format_carbs, carbsAfterConstraints) + "</font>")
            if (carbsAfterConstraints != carbs)
                actions.add("<font color='" + resourceHelper.gc(R.color.warning) + "'>" + resourceHelper.gs(R.string.carbsconstraintapplied) + "</font>")
        }
        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)

        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        if (carbsAfterConstraints > 0 || activitySelected || eatingSoonSelected || hypoSelected) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.carbs), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    when {
                        activitySelected   -> {
                            aapsLogger.debug("USER ENTRY: TEMPTARGET ACTIVITY $activityTT duration: $activityTTDuration")
                            val tempTarget = TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(activityTTDuration)
                                .reason(resourceHelper.gs(R.string.activity))
                                .source(Source.USER)
                                .low(Profile.toMgdl(activityTT, profileFunction.getUnits()))
                                .high(Profile.toMgdl(activityTT, profileFunction.getUnits()))
                            treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                        }

                        eatingSoonSelected -> {
                            aapsLogger.debug("USER ENTRY: TEMPTARGET EATING SOON $eatingSoonTT duration: $eatingSoonTTDuration")
                            val tempTarget = TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(eatingSoonTTDuration)
                                .reason(resourceHelper.gs(R.string.eatingsoon))
                                .source(Source.USER)
                                .low(Profile.toMgdl(eatingSoonTT, profileFunction.getUnits()))
                                .high(Profile.toMgdl(eatingSoonTT, profileFunction.getUnits()))
                            treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                        }

                        hypoSelected       -> {
                            aapsLogger.debug("USER ENTRY: TEMPTARGET HYPO $hypoTT duration: $hypoTTDuration")
                            val tempTarget = TempTarget()
                                .date(System.currentTimeMillis())
                                .duration(hypoTTDuration)
                                .reason(resourceHelper.gs(R.string.hypo))
                                .source(Source.USER)
                                .low(Profile.toMgdl(hypoTT, profileFunction.getUnits()))
                                .high(Profile.toMgdl(hypoTT, profileFunction.getUnits()))
                            treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                        }
                    }
                    if (carbsAfterConstraints > 0) {
                        if (duration == 0) {
                            aapsLogger.debug("USER ENTRY: CARBS $carbsAfterConstraints time: $time")
                            carbsGenerator.createCarb(carbsAfterConstraints, time, CareportalEvent.CARBCORRECTION, notes)
                        } else {
                            aapsLogger.debug("USER ENTRY: CARBS $carbsAfterConstraints time: $time duration: $duration")
                            carbsGenerator.generateCarbs(carbsAfterConstraints, time, duration, notes)
                            nsUpload.uploadEvent(CareportalEvent.NOTE, DateUtil.now() - 2000, resourceHelper.gs(R.string.generated_ecarbs_note, carbsAfterConstraints, duration, timeOffset))
                        }
                    }
                }, null)
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, resourceHelper.gs(R.string.carbs), resourceHelper.gs(R.string.no_action_selected))
            }
        return true
    }
}