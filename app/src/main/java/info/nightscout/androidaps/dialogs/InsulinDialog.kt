package info.nightscout.androidaps.dialogs

import android.content.Intent
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
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConfigBuilderPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import kotlinx.android.synthetic.main.dialog_insulin.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max

class InsulinDialog : DialogFragmentWithDate() {

    companion object {
        private const val PLUS1_DEFAULT = 0.5
        private const val PLUS2_DEFAULT = 1.0
        private const val PLUS3_DEFAULT = 2.0
    }

    private val maxInsulin = MainApp.getConstraintChecker().maxBolusAllowed.value()

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            validateInputs()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs() {
        if (abs(overview_insulin_time.value.toInt()) > 12 * 60) {
            overview_insulin_time.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.constraintapllied))
        }
        if (overview_insulin_amount.value > maxInsulin) {
            overview_insulin_amount.value = 0.0
            ToastUtils.showToastInUiThread(MainApp.instance().applicationContext, MainApp.gs(R.string.bolusconstraintapplied))
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("overview_insulin_time", overview_insulin_time.value)
        savedInstanceState.putDouble("overview_insulin_amount", overview_insulin_amount.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_insulin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        overview_insulin_time.setParams(savedInstanceState?.getDouble("overview_insulin_time")
            ?: 0.0, -12 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, ok, textWatcher)
        overview_insulin_amount.setParams(savedInstanceState?.getDouble("overview_insulin_amount")
            ?: 0.0, 0.0, maxInsulin, ConfigBuilderPlugin.getPlugin().activePump!!.pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(), false, ok, textWatcher)

        overview_insulin_plus05.text = toSignedString(SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT))
        overview_insulin_plus05.setOnClickListener {
            overview_insulin_amount.value = max(0.0, overview_insulin_amount.value
                + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT))
            validateInputs()
        }
        overview_insulin_plus10.text = toSignedString(SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT))
        overview_insulin_plus10.setOnClickListener {
            overview_insulin_amount.value = max(0.0, overview_insulin_amount.value
                + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT))
            validateInputs()
        }
        overview_insulin_plus20.text = toSignedString(SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT))
        overview_insulin_plus20.setOnClickListener {
            overview_insulin_amount.value = Math.max(0.0, overview_insulin_amount.value
                + SP.getDouble(MainApp.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT))
            validateInputs()
        }

        overview_insulin_time_layout.visibility = View.GONE
        overview_insulin_record_only.setOnCheckedChangeListener { _, isChecked: Boolean ->
            overview_insulin_time_layout.visibility = isChecked.toVisibility()
        }
    }

    private fun toSignedString(value: Double): String {
        val formatted = DecimalFormatter.toPumpSupportedBolus(value)
        return if (value > 0) "+$formatted" else formatted
    }

    override fun submit(): Boolean {
        val pumpDescription = ConfigBuilderPlugin.getPlugin().activePump?.pumpDescription
            ?: return false
        val insulin = SafeParse.stringToDouble(overview_insulin_amount.text)
        val insulinAfterConstraints = MainApp.getConstraintChecker().applyBolusConstraints(Constraint(insulin)).value()
        val actions: LinkedList<String?> = LinkedList()
        val units = ProfileFunctions.getSystemUnits()
        val unitLabel = if (units == Constants.MMOL) MainApp.gs(R.string.mmol) else MainApp.gs(R.string.mgdl)
        val recordOnlyChecked = overview_insulin_record_only.isChecked
        val eatingSoonChecked = overview_insulin_start_eating_soon_tt.isChecked

        if (insulinAfterConstraints > 0) {
            actions.add(MainApp.gs(R.string.bolus) + ": " + "<font color='" + MainApp.gc(R.color.bolus) + "'>" + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints) + MainApp.gs(R.string.insulin_unit_shortname) + "</font>")
            if (recordOnlyChecked)
                actions.add("<font color='" + MainApp.gc(R.color.warning) + "'>" + MainApp.gs(R.string.bolusrecordedonly) + "</font>")
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(MainApp.gs(R.string.bolusconstraintappliedwarning, MainApp.gc(R.color.warning), insulin, insulinAfterConstraints))
        }
        val eatingSoonTTDuration = DefaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = DefaultValueHelper.determineEatingSoonTT()
        if (eatingSoonChecked)
            actions.add(MainApp.gs(R.string.temptargetshort) + ": " + "<font color='" + MainApp.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + eatingSoonTTDuration + " " + MainApp.gs(R.string.unit_minute_short) + ")</font>")

        val timeOffset = overview_insulin_time.value.toInt()
        val time = DateUtil.now() + T.mins(timeOffset.toLong()).msecs()
        if (timeOffset != 0)
            actions.add(MainApp.gs(R.string.time) + ": " + DateUtil.dateAndTimeString(time))

        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(MainApp.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)

        if (insulinAfterConstraints > 0 || eatingSoonChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, MainApp.gs(R.string.bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    if (eatingSoonChecked) {
                        log.debug("USER ENTRY: TEMPTARGET EATING SOON $eatingSoonTT duration: $eatingSoonTTDuration")
                        val tempTarget = TempTarget()
                            .date(System.currentTimeMillis())
                            .duration(eatingSoonTTDuration)
                            .reason(MainApp.gs(R.string.eatingsoon))
                            .source(Source.USER)
                            .low(Profile.toMgdl(eatingSoonTT, ProfileFunctions.getSystemUnits()))
                            .high(Profile.toMgdl(eatingSoonTT, ProfileFunctions.getSystemUnits()))
                        TreatmentsPlugin.getPlugin().addToHistoryTempTarget(tempTarget)
                    }
                    if (insulinAfterConstraints > 0) {
                        val detailedBolusInfo = DetailedBolusInfo()
                        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS
                        detailedBolusInfo.insulin = insulinAfterConstraints
                        detailedBolusInfo.context = context
                        detailedBolusInfo.source = Source.USER
                        detailedBolusInfo.notes = notes
                        if (recordOnlyChecked) {
                            log.debug("USER ENTRY: BOLUS RECORD ONLY $insulinAfterConstraints")
                            detailedBolusInfo.date = time
                            TreatmentsPlugin.getPlugin().addToHistoryTreatment(detailedBolusInfo, false)
                        } else {
                            log.debug("USER ENTRY: BOLUS $insulinAfterConstraints")
                            detailedBolusInfo.date = DateUtil.now()
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
                        }
                    }
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, MainApp.gs(R.string.bolus), MainApp.gs(R.string.no_action_selected))
            }
        return true
    }
}