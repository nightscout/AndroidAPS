package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionCheck.Protection.BOLUS
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogCarbsBinding
import com.google.common.base.Joiner
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.max

class CarbsDialog : DialogFragmentWithDate() {

    @Inject lateinit var ctx: Context
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var glucoseStatusProvider: GlucoseStatusProvider
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var automation: Automation
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var queryingProtection = false
    private val disposable = CompositeDisposable()

    private val textWatcher: TextWatcher = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            _binding?.let {
                validateInputs()
            }
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
    }

    private fun validateInputs() {
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        val time = binding.time.value.toInt()
        if (time > 12 * 60 || time < -7 * 24 * 60) {
            binding.time.value = 0.0
            ToastUtils.warnToast(ctx, app.aaps.core.ui.R.string.constraint_applied)
        }
        if (binding.duration.value > 10) {
            binding.duration.value = 0.0
            ToastUtils.warnToast(ctx, app.aaps.core.ui.R.string.constraint_applied)
        }
        if (binding.carbs.value.toInt() > maxCarbs) {
            binding.carbs.value = 0.0
            ToastUtils.warnToast(ctx, R.string.carbs_constraint_applied)
        }
    }

    private var _binding: DialogCarbsBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("time", binding.time.value)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putDouble("carbs", binding.carbs.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogCarbsBinding.inflate(inflater, container, false)
        binding.time.setOnValueChangedListener { timeOffset: Double ->
            run {
                val newTime = eventTimeOriginal + timeOffset.toLong() * 1000 * 60
                updateDateTime(newTime)
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (preferences.get(BooleanKey.OverviewUseBolusReminder)) {
            glucoseStatusProvider.glucoseStatusData?.let { glucoseStatus ->
                if (glucoseStatus.glucose + 3 * glucoseStatus.delta < 70.0)
                    binding.bolusReminder.visibility = View.VISIBLE
            }
        }
        val maxCarbs = constraintChecker.getMaxCarbsAllowed().value().toDouble()
        binding.time.setParams(
            savedInstanceState?.getDouble("time")
                ?: 0.0, -7 * 24 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
                ?: 0.0, 0.0, HardLimits.MAX_CARBS_DURATION_HOURS.toDouble(), 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )

        binding.carbs.setParams(
            savedInstanceState?.getDouble("carbs")
                ?: 0.0, -maxCarbs, maxCarbs, 1.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )
        val plus1text = toSignedString(preferences.get(IntKey.OverviewCarbsButtonIncrement1))
        binding.plus1.text = plus1text
        binding.plus1.contentDescription = rh.gs(app.aaps.core.ui.R.string.carbs) + " " + plus1text
        binding.plus1.setOnClickListener {
            binding.carbs.value = max(0.0, binding.carbs.value + preferences.get(IntKey.OverviewCarbsButtonIncrement1))
            validateInputs()
            binding.carbs.announceValue()
        }

        val plus2text = toSignedString(preferences.get(IntKey.OverviewCarbsButtonIncrement2))
        binding.plus2.text = plus2text
        binding.plus2.contentDescription = rh.gs(app.aaps.core.ui.R.string.carbs) + " " + plus2text
        binding.plus2.setOnClickListener {
            binding.carbs.value = max(0.0, binding.carbs.value + preferences.get(IntKey.OverviewCarbsButtonIncrement2))
            validateInputs()
            binding.carbs.announceValue()
        }
        val plus3text = toSignedString(preferences.get(IntKey.OverviewCarbsButtonIncrement3))
        binding.plus3.text = plus3text
        binding.plus3.contentDescription = rh.gs(app.aaps.core.ui.R.string.carbs) + " " + plus3text
        binding.plus3.setOnClickListener {
            binding.carbs.value = max(0.0, binding.carbs.value + preferences.get(IntKey.OverviewCarbsButtonIncrement3))
            validateInputs()
            binding.carbs.announceValue()
        }

        setOnValueChangedListener { eventTime: Long ->
            run {
                val timeOffset = ((eventTime - eventTimeOriginal) / (1000 * 60)).toDouble()
                if (_binding != null) binding.time.value = timeOffset
            }
        }

        iobCobCalculator.ads.actualBg()?.let { bgReading ->

            if (bgReading.recalculated < 72) {

                val activeTT = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())
                val hypoTTDuration = preferences.get(IntKey.OverviewHypoDuration)

                var shouldAutoCheckHypo = true

                if (activeTT != null) {

                    val activeTarget = activeTT.highTarget
                    val now = System.currentTimeMillis()
                    val remainingDurationMin =
                        ((activeTT.timestamp + activeTT.duration) - now) / 60000

                    // Prevent auto-checking HypoTT when:
                    // 1. Active TT target is above Constants.ALLOW_SMB_WITH_HIGH_TT
                    // 2. Active TT lasts longer than the hypoTT preset
                    if (activeTarget > Constants.ALLOW_SMB_WITH_HIGH_TT && remainingDurationMin > hypoTTDuration) {
                        shouldAutoCheckHypo = false
                    }
                }

                if (shouldAutoCheckHypo) {
                    binding.hypoTt.isChecked = true
                }
            }
        }
        binding.hypoTt.setOnClickListener {
            binding.activityTt.isChecked = false
            binding.eatingSoonTt.isChecked = false
        }
        binding.activityTt.setOnClickListener {
            binding.hypoTt.isChecked = false
            binding.eatingSoonTt.isChecked = false
        }
        binding.eatingSoonTt.setOnClickListener {
            binding.hypoTt.isChecked = false
            binding.activityTt.isChecked = false
        }
        binding.durationLabel.labelFor = binding.duration.editTextId
        binding.timeLabel.labelFor = binding.time.editTextId
        binding.carbsLabel.labelFor = binding.carbs.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    private fun toSignedString(value: Int): String {
        return if (value > 0) "+$value" else value.toString()
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val carbs = binding.carbs.value.toInt()
        var carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(carbs, aapsLogger)).value()
        val units = profileUtil.units
        val cob = iobCobCalculator.ads.getLastAutosensData("carbsDialog", aapsLogger, dateUtil)?.cob ?: 0.0
        val activityTTDuration = preferences.get(IntKey.OverviewActivityDuration)
        val activityTT = preferences.get(UnitDoubleKey.OverviewActivityTarget)
        val eatingSoonTTDuration = preferences.get(IntKey.OverviewEatingSoonDuration)
        val eatingSoonTT = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)
        val hypoTTDuration = preferences.get(IntKey.OverviewHypoDuration)
        val hypoTT = preferences.get(UnitDoubleKey.OverviewHypoTarget)
        val actions: LinkedList<String?> = LinkedList()
        val unitLabel = if (units == GlucoseUnit.MMOL) rh.gs(app.aaps.core.ui.R.string.mmol) else rh.gs(app.aaps.core.ui.R.string.mgdl)
        val useAlarm = binding.alarmCheckBox.isChecked
        val remindBolus = binding.bolusReminderCheckBox.isChecked

        val activitySelected = binding.activityTt.isChecked
        if (activitySelected)
            actions.add(
                rh.gs(R.string.temp_target_short) + ": " + (decimalFormatter.to1Decimal(activityTT) + " " + unitLabel + " (" + rh.gs(
                    app.aaps.core.ui.R.string.format_mins,
                    activityTTDuration
                ) + ")").formatColor(
                    context,
                    rh,
                    app.aaps.core.ui.R.attr.tempTargetConfirmation
                )
            )
        val eatingSoonSelected = binding.eatingSoonTt.isChecked
        if (eatingSoonSelected)
            actions.add(
                rh.gs(R.string.temp_target_short) + ": " + (decimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + rh.gs(
                    app.aaps.core.ui.R.string.format_mins,
                    eatingSoonTTDuration
                ) + ")").formatColor(context, rh, app.aaps.core.ui.R.attr.tempTargetConfirmation)
            )
        val hypoSelected = binding.hypoTt.isChecked
        if (hypoSelected)
            actions.add(
                rh.gs(R.string.temp_target_short) + ": " + (decimalFormatter.to1Decimal(hypoTT) + " " + unitLabel + " (" + rh.gs(
                    app.aaps.core.ui.R.string.format_mins,
                    hypoTTDuration
                ) + ")").formatColor(
                    context,
                    rh,
                    app.aaps.core.ui.R.attr.tempTargetConfirmation
                )
            )

        val timeOffset = binding.time.value.toInt()
        if (useAlarm && carbs > 0 && timeOffset > 0)
            actions.add(rh.gs(app.aaps.core.ui.R.string.alarminxmin, timeOffset).formatColor(context, rh, app.aaps.core.ui.R.attr.infoColor))
        val duration = binding.duration.value.toInt()
        if (duration > 0)
            actions.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + duration + rh.gs(app.aaps.core.interfaces.R.string.shorthour))
        if (carbsAfterConstraints > 0) {
            actions.add(
                rh.gs(app.aaps.core.ui.R.string.carbs) + ": " + "<font color='" + rh.gac(
                    context,
                    app.aaps.core.ui.R.attr.carbsColor
                ) + "'>" + rh.gs(app.aaps.core.objects.R.string.format_carbs, carbsAfterConstraints) + "</font>"
            )
            if (carbsAfterConstraints != carbs)
                actions.add("<font color='" + rh.gac(context, app.aaps.core.ui.R.attr.warningColor) + "'>" + rh.gs(R.string.carbs_constraint_applied) + "</font>")
        }
        if (carbsAfterConstraints < 0) {
            if (carbsAfterConstraints < -cob) carbsAfterConstraints = ceil(-cob).toInt()
            if (timeOffset != 0) carbsAfterConstraints = 0
            actions.add(
                rh.gs(app.aaps.core.ui.R.string.carbs) + ": " + "<font color='" + rh.gac(
                    context,
                    app.aaps.core.ui.R.attr.warningColor
                ) + "'>" + rh.gs(app.aaps.core.objects.R.string.format_carbs, carbsAfterConstraints) + "</font>"
            )
            if (carbsAfterConstraints != carbs)
                actions.add("<font color='" + rh.gac(context, app.aaps.core.ui.R.attr.warningColor) + "'>" + rh.gs(R.string.carbs_constraint_applied) + "</font>")
        }
        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(rh.gs(app.aaps.core.ui.R.string.notes_label) + ": " + notes)

        if (eventTimeChanged)
            actions.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        if (carbsAfterConstraints != 0 || activitySelected || eatingSoonSelected || hypoSelected) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.carbs), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    val selectedTTDuration = when {
                        activitySelected   -> activityTTDuration
                        eatingSoonSelected -> eatingSoonTTDuration
                        hypoSelected       -> hypoTTDuration
                        else               -> 0
                    }
                    val selectedTT = when {
                        activitySelected   -> activityTT
                        eatingSoonSelected -> eatingSoonTT
                        hypoSelected       -> hypoTT
                        else               -> 0.0
                    }
                    val reason = when {
                        activitySelected   -> TT.Reason.ACTIVITY
                        eatingSoonSelected -> TT.Reason.EATING_SOON
                        hypoSelected       -> TT.Reason.HYPOGLYCEMIA
                        else               -> TT.Reason.CUSTOM
                    }
                    if (reason != TT.Reason.CUSTOM)
                        disposable += persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                            temporaryTarget = TT(
                                timestamp = System.currentTimeMillis(),
                                duration = TimeUnit.MINUTES.toMillis(selectedTTDuration.toLong()),
                                reason = reason,
                                lowTarget = profileUtil.convertToMgdl(selectedTT, profileUtil.units),
                                highTarget = profileUtil.convertToMgdl(selectedTT, profileUtil.units)
                            ),
                            action = Action.TT,
                            source = Sources.CarbDialog,
                            note = null,
                            listValues = listOf(
                                ValueWithUnit.TETTReason(reason),
                                ValueWithUnit.fromGlucoseUnit(selectedTT, units),
                                ValueWithUnit.Minute(selectedTTDuration)
                            )
                        ).subscribe()
                    if (carbsAfterConstraints != 0) {
                        val detailedBolusInfo = DetailedBolusInfo().also {
                            it.eventType = TE.Type.CORRECTION_BOLUS
                            it.carbs = carbsAfterConstraints.toDouble()
                            it.context = context
                            it.notes = notes
                            it.carbsDuration = T.hours(duration.toLong()).msecs()
                            it.carbsTimestamp = eventTime
                        }
                        uel.log(
                            action = if (duration == 0) Action.CARBS else Action.EXTENDED_CARBS, source = Sources.CarbDialog,
                            note = notes,
                            listValues = listOfNotNull(
                                ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                                ValueWithUnit.Gram(carbsAfterConstraints),
                                ValueWithUnit.Minute(timeOffset).takeIf { timeOffset != 0 },
                                ValueWithUnit.Hour(duration).takeIf { duration != 0 }
                            )
                        )
                        commandQueue.bolus(detailedBolusInfo, object : Callback() {
                            override fun run() {
                                automation.removeAutomationEventEatReminder()
                                if (!result.success) {
                                    uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                                } else if (preferences.get(BooleanKey.OverviewUseBolusReminder) && remindBolus)
                                    automation.scheduleAutomationEventBolusReminder()
                            }
                        })
                    }
                    if (useAlarm && carbs > 0 && timeOffset > 0) {
                        automation.scheduleTimeToEatReminder(T.mins(timeOffset.toLong()).secs().toInt())
                    }
                }, null)
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(app.aaps.core.ui.R.string.carbs), rh.gs(app.aaps.core.ui.R.string.no_action_selected))
            }
        return true
    }

    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(ctx, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}
