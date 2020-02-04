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
import info.nightscout.androidaps.db.DatabaseHelper
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.treatments.CarbsGenerator
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.*
import kotlinx.android.synthetic.main.dialog_carbs.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import kotlin.math.max

class CarbsDialog : DialogFragmentWithDate() {

    companion object {
        private const val FAV1_DEFAULT = 5
        private const val FAV2_DEFAULT = 10
        private const val FAV3_DEFAULT = 20
    }

    private val maxCarbs = MainApp.getConstraintChecker().maxCarbsAllowed.value().toDouble()

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            validateInputs()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs() {
        val time = overview_carbs_time.value.toInt()
        if (time > 12 * 60 || time < -12 * 60) {
            overview_carbs_time.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.constraintapllied))
        }
        if (overview_carbs_duration.value > 10) {
            overview_carbs_duration.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.constraintapllied))
        }
        if (overview_carbs_carbs.value.toInt() > maxCarbs) {
            overview_carbs_carbs.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.carbsconstraintapplied))
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

        overview_carbs_time.setParams(savedInstanceState?.getDouble("overview_carbs_time")
            ?: 0.0, -12 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, ok, textWatcher)

        overview_carbs_duration.setParams(savedInstanceState?.getDouble("overview_carbs_duration")
            ?: 0.0, 0.0, 10.0, 1.0, DecimalFormat("0"), false, ok, textWatcher)

        overview_carbs_carbs.setParams(savedInstanceState?.getDouble("overview_carbs_carbs")
            ?: 0.0, 0.0, maxCarbs, 1.0, DecimalFormat("0"), false, ok, textWatcher)

        overview_carbs_plus1.text = toSignedString(SP.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT))
        overview_carbs_plus1.setOnClickListener {
            overview_carbs_carbs.value = max(0.0, overview_carbs_carbs.value
                + SP.getInt(R.string.key_carbs_button_increment_1, FAV1_DEFAULT))
            validateInputs()
        }

        overview_carbs_plus2.text = toSignedString(SP.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT))
        overview_carbs_plus2.setOnClickListener {
            overview_carbs_carbs.value = max(0.0, overview_carbs_carbs.value
                + SP.getInt(R.string.key_carbs_button_increment_2, FAV2_DEFAULT))
            validateInputs()
        }

        overview_carbs_plus3.text = toSignedString(SP.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT))
        overview_carbs_plus3.setOnClickListener {
            overview_carbs_carbs.value = max(0.0, overview_carbs_carbs.value
                + SP.getInt(R.string.key_carbs_button_increment_3, FAV3_DEFAULT))
            validateInputs()
        }

        DatabaseHelper.actualBg()?.let { bgReading ->
            if (bgReading.value < 72)
                overview_carbs_hypo_tt.setChecked(true)
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
        val carbs = overview_carbs_carbs.value.toInt()
        val carbsAfterConstraints = MainApp.getConstraintChecker().applyCarbsConstraints(Constraint(carbs)).value()
        val units = ProfileFunctions.getSystemUnits()
        val activityTTDuration = DefaultValueHelper.determineActivityTTDuration()
        val activityTT = DefaultValueHelper.determineActivityTT()
        val eatingSoonTTDuration = DefaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = DefaultValueHelper.determineEatingSoonTT()
        val hypoTTDuration = DefaultValueHelper.determineHypoTTDuration()
        val hypoTT = DefaultValueHelper.determineHypoTT()
        val actions: LinkedList<String?> = LinkedList()
        val unitLabel = if (units == Constants.MMOL) MainApp.gs(R.string.mmol) else MainApp.gs(R.string.mgdl)

        val activitySelected = overview_carbs_activity_tt.isChecked
        if (activitySelected)
            actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(activityTT) + " " + unitLabel + " (" + activityTTDuration + " " + MainApp.gs(R.string.unit_minute_short) + ")</font>")
        val eatingSoonSelected = overview_carbs_eating_soon_tt.isChecked
        if (eatingSoonSelected)
            actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + eatingSoonTTDuration + " " + MainApp.gs(R.string.unit_minute_short) + ")</font>")
        val hypoSelected = overview_carbs_hypo_tt.isChecked
        if (hypoSelected)
            actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(hypoTT) + " " + unitLabel + " (" + hypoTTDuration + " " + MainApp.gs(R.string.unit_minute_short) + ")</font>")

        val timeOffset = overview_carbs_time.value.toInt()
        val time = eventTime + timeOffset * 1000 * 60
        if (timeOffset != 0)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(time))
        val duration = overview_carbs_duration.value.toInt()
        if (duration > 0)
            actions.add(MainApp.gs(R.string.duration) + ": " + duration + MainApp.gs(R.string.shorthour))
        if (carbsAfterConstraints > 0) {
            actions.add(MainApp.gs(R.string.carbs) + ": " + "<font color='" + MainApp.gc(R.color.carbs) + "'>" + MainApp.gs(R.string.format_carbs, carbsAfterConstraints) + "</font>")
            if (carbsAfterConstraints != carbs)
                actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.carbsconstraintapplied) + "</font>")
        }
        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)

        if (eventTimeChanged)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(eventTime))

        if (carbsAfterConstraints > 0 || activitySelected || eatingSoonSelected || hypoSelected) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, MainApp.gs(R.string.carbs), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    if (activitySelected) {
                        log.debug("USER ENTRY: TEMPTARGET ACTIVITY $activityTT duration: $activityTTDuration")
                        val tempTarget = TempTarget()
                            .date(eventTime)
                            .duration(activityTTDuration)
                            .reason(MainApp.gs(R.string.activity))
                            .source(Source.USER)
                            .low(Profile.toMgdl(activityTT, ProfileFunctions.getSystemUnits()))
                            .high(Profile.toMgdl(activityTT, ProfileFunctions.getSystemUnits()))
                        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                    } else if (eatingSoonSelected) {
                        log.debug("USER ENTRY: TEMPTARGET EATING SOON $eatingSoonTT duration: $eatingSoonTTDuration")
                        val tempTarget = TempTarget()
                            .date(eventTime)
                            .duration(eatingSoonTTDuration)
                            .reason(MainApp.gs(R.string.eatingsoon))
                            .source(Source.USER)
                            .low(Profile.toMgdl(eatingSoonTT, ProfileFunctions.getSystemUnits()))
                            .high(Profile.toMgdl(eatingSoonTT, ProfileFunctions.getSystemUnits()))
                        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                    } else if (hypoSelected) {
                        log.debug("USER ENTRY: TEMPTARGET HYPO $hypoTT duration: $hypoTTDuration")
                        val tempTarget = TempTarget()
                            .date(eventTime)
                            .duration(hypoTTDuration)
                            .reason(MainApp.gs(R.string.hypo))
                            .source(Source.USER)
                            .low(Profile.toMgdl(hypoTT, ProfileFunctions.getSystemUnits()))
                            .high(Profile.toMgdl(hypoTT, ProfileFunctions.getSystemUnits()))
                        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                    }
                    if (carbsAfterConstraints > 0) {
                        if (duration == 0) {
                            log.debug("USER ENTRY: CARBS $carbsAfterConstraints time: $time")
                            CarbsGenerator.createCarb(carbsAfterConstraints, time, CareportalEvent.CARBCORRECTION, notes)
                        } else {
                            log.debug("USER ENTRY: CARBS $carbsAfterConstraints time: $time duration: $duration")
                            CarbsGenerator.generateCarbs(carbsAfterConstraints, time, duration, notes)
                            NSUpload.uploadEvent(CareportalEvent.NOTE, time - 2000, MainApp.gs(R.string.generated_ecarbs_note, carbsAfterConstraints, duration, timeOffset))
                        }
                    }
                }, null)
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, MainApp.gs(R.string.carbs), MainApp.gs(R.string.no_action_selected))
            }
        return true
    }
}