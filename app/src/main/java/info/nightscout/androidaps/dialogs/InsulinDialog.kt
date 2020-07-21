package info.nightscout.androidaps.dialogs

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.toSignedString
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import kotlinx.android.synthetic.main.dialog_insulin.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

class InsulinDialog : DialogFragmentWithDate() {
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var ctx: Context
    companion object {
        private const val PLUS1_DEFAULT = 0.5
        private const val PLUS2_DEFAULT = 1.0
        private const val PLUS3_DEFAULT = 2.0
    }

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            validateInputs()
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs() {
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (abs(overview_insulin_time.value.toInt()) > 12 * 60) {
            overview_insulin_time.value = 0.0
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.constraintapllied))
        }
        if (overview_insulin_amount.value > maxInsulin) {
            overview_insulin_amount.value = 0.0
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.bolusconstraintapplied))
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

        val themeToSet = sp.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        try {
            val theme: Resources.Theme? = context?.getTheme()
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            if (theme != null) {
                theme.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return inflater.inflate(R.layout.dialog_insulin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()

        overview_insulin_time.setParams(savedInstanceState?.getDouble("overview_insulin_time")
            ?: 0.0, -12 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, ok, textWatcher)
        overview_insulin_amount.setParams(savedInstanceState?.getDouble("overview_insulin_amount")
            ?: 0.0, 0.0, maxInsulin, activePlugin.activePump.pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, ok, textWatcher)

        overview_insulin_plus05.text = sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT).toSignedString(activePlugin.activePump)
        overview_insulin_plus05.setOnClickListener {
            overview_insulin_amount.value = max(0.0, overview_insulin_amount.value
                + sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT))
            validateInputs()
        }
        overview_insulin_plus10.text = sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT).toSignedString(activePlugin.activePump)
        overview_insulin_plus10.setOnClickListener {
            overview_insulin_amount.value = max(0.0, overview_insulin_amount.value
                + sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT))
            validateInputs()
        }
        overview_insulin_plus20.text = sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT).toSignedString(activePlugin.activePump)
        overview_insulin_plus20.setOnClickListener {
            overview_insulin_amount.value = max(0.0, overview_insulin_amount.value
                + sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT))
            validateInputs()
        }

        overview_insulin_time_layout.visibility = View.GONE
        overview_insulin_record_only.setOnCheckedChangeListener { _, isChecked: Boolean ->
            overview_insulin_time_layout.visibility = isChecked.toVisibility()
        }
    }

    override fun submit(): Boolean {
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = SafeParse.stringToDouble(overview_insulin_amount.text)
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        val actions: LinkedList<String?> = LinkedList()
        val units = profileFunction.getUnits()
        val unitLabel = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)
        val recordOnlyChecked = overview_insulin_record_only.isChecked
        val eatingSoonChecked = overview_insulin_start_eating_soon_tt.isChecked

        if (insulinAfterConstraints > 0) {
            actions.add(resourceHelper.gs(R.string.bolus) + ": " + "<font color='" + resourceHelper.gc(R.color.bolus) + "'>" + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump) + resourceHelper.gs(R.string.insulin_unit_shortname) + "</font>")
            if (recordOnlyChecked)
                actions.add("<font color='" + resourceHelper.gc(R.color.warning) + "'>" + resourceHelper.gs(R.string.bolusrecordedonly) + "</font>")
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(resourceHelper.gs(R.string.bolusconstraintappliedwarning, resourceHelper.gc(R.color.warning), insulin, insulinAfterConstraints))
        }
        val eatingSoonTTDuration = defaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = defaultValueHelper.determineEatingSoonTT()
        if (eatingSoonChecked)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + "<font color='" + resourceHelper.gc(R.color.tempTargetConfirmation) + "'>" + DecimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + eatingSoonTTDuration + " " + resourceHelper.gs(R.string.unit_minute_short) + ")</font>")

        val timeOffset = overview_insulin_time.value.toInt()
        val time = DateUtil.now() + T.mins(timeOffset.toLong()).msecs()
        if (timeOffset != 0)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(time))

        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)

        if (insulinAfterConstraints > 0 || eatingSoonChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    if (eatingSoonChecked) {
                        aapsLogger.debug("USER ENTRY: TEMPTARGET EATING SOON $eatingSoonTT duration: $eatingSoonTTDuration")
                        val tempTarget = TempTarget()
                            .date(System.currentTimeMillis())
                            .duration(eatingSoonTTDuration)
                            .reason(resourceHelper.gs(R.string.eatingsoon))
                            .source(Source.USER)
                            .low(Profile.toMgdl(eatingSoonTT, profileFunction.getUnits()))
                            .high(Profile.toMgdl(eatingSoonTT, profileFunction.getUnits()))
                        activePlugin.activeTreatments.addToHistoryTempTarget(tempTarget)
                    }
                    if (insulinAfterConstraints > 0) {
                        val detailedBolusInfo = DetailedBolusInfo()
                        detailedBolusInfo.eventType = CareportalEvent.CORRECTIONBOLUS
                        detailedBolusInfo.insulin = insulinAfterConstraints
                        detailedBolusInfo.context = context
                        detailedBolusInfo.source = Source.USER
                        detailedBolusInfo.notes = notes
                        if (recordOnlyChecked) {
                            aapsLogger.debug("USER ENTRY: BOLUS RECORD ONLY $insulinAfterConstraints")
                            detailedBolusInfo.date = time
                            activePlugin.activeTreatments.addToHistoryTreatment(detailedBolusInfo, false)
                        } else {
                            aapsLogger.debug("USER ENTRY: BOLUS $insulinAfterConstraints")
                            detailedBolusInfo.date = DateUtil.now()
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
                        }
                    }
                }, null, sp)
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, resourceHelper.gs(R.string.bolus), resourceHelper.gs(R.string.no_action_selected), null, sp)
            }
        return true
    }
}