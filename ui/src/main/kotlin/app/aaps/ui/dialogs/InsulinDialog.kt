package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.Constants.INSULIN_PLUS1_DEFAULT
import app.aaps.core.interfaces.configuration.Constants.INSULIN_PLUS2_DEFAULT
import app.aaps.core.interfaces.configuration.Constants.INSULIN_PLUS3_DEFAULT
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.extensions.toVisibility
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.queue.Callback
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.SafeParse
import app.aaps.core.interfaces.utils.T
import app.aaps.core.main.constraints.ConstraintObject
import app.aaps.core.main.utils.extensions.formatColor
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogInsulinBinding
import app.aaps.ui.extensions.toSignedString
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max

class InsulinDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var ctx: Context
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var config: Config
    @Inject lateinit var automation: Automation
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var injector: HasAndroidInjector

    private var queryingProtection = false
    private val disposable = CompositeDisposable()
    private var _binding: DialogInsulinBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

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
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (abs(binding.time.value.toInt()) > 12 * 60) {
            binding.time.value = 0.0
            ToastUtils.warnToast(context, app.aaps.core.ui.R.string.constraint_applied)
        }
        if (binding.amount.value > maxInsulin) {
            binding.amount.value = 0.0
            ToastUtils.warnToast(context, R.string.bolus_constraint_applied)
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("time", binding.time.value)
        savedInstanceState.putDouble("amount", binding.amount.value)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        onCreateViewGeneral()
        _binding = DialogInsulinBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (config.NSCLIENT) {
            binding.recordOnly.isChecked = true
            binding.recordOnly.isEnabled = false
        }
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()

        binding.time.setParams(
            savedInstanceState?.getDouble("time")
                ?: 0.0, -12 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher
        )
        binding.amount.setParams(
            savedInstanceState?.getDouble("amount")
                ?: 0.0, 0.0, maxInsulin, activePlugin.activePump.pumpDescription.bolusStep, decimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump.pumpDescription.bolusStep), false, binding
                .okcancel.ok, textWatcher
        )

        val plus05Text = sp.getDouble(rh.gs(app.aaps.core.interfaces.R.string.key_insulin_button_increment_1), INSULIN_PLUS1_DEFAULT).toSignedString(activePlugin.activePump, decimalFormatter)
        binding.plus05.text = plus05Text
        binding.plus05.contentDescription = rh.gs(app.aaps.core.ui.R.string.overview_insulin_label) + " " + plus05Text
        binding.plus05.setOnClickListener {
            binding.amount.value = max(
                0.0, binding.amount.value
                    + sp.getDouble(rh.gs(app.aaps.core.interfaces.R.string.key_insulin_button_increment_1), INSULIN_PLUS1_DEFAULT)
            )
            validateInputs()
            binding.amount.announceValue()
        }
        val plus10Text = sp.getDouble(rh.gs(app.aaps.core.interfaces.R.string.key_insulin_button_increment_2), INSULIN_PLUS2_DEFAULT).toSignedString(activePlugin.activePump, decimalFormatter)
        binding.plus10.text = plus10Text
        binding.plus10.contentDescription = rh.gs(app.aaps.core.ui.R.string.overview_insulin_label) + " " + plus10Text
        binding.plus10.setOnClickListener {
            binding.amount.value = max(
                0.0, binding.amount.value
                    + sp.getDouble(rh.gs(app.aaps.core.interfaces.R.string.key_insulin_button_increment_2), INSULIN_PLUS2_DEFAULT)
            )
            validateInputs()
            binding.amount.announceValue()
        }
        val plus20Text = sp.getDouble(rh.gs(app.aaps.core.interfaces.R.string.key_insulin_button_increment_3), INSULIN_PLUS3_DEFAULT).toSignedString(activePlugin.activePump, decimalFormatter)
        binding.plus20.text = plus20Text
        binding.plus20.contentDescription = rh.gs(app.aaps.core.ui.R.string.overview_insulin_label) + " " + plus20Text
        binding.plus20.setOnClickListener {
            binding.amount.value = max(
                0.0, binding.amount.value
                    + sp.getDouble(rh.gs(app.aaps.core.interfaces.R.string.key_insulin_button_increment_3), INSULIN_PLUS3_DEFAULT)
            )
            validateInputs()
            binding.amount.announceValue()
        }

        binding.timeLayout.visibility = View.GONE
        binding.recordOnly.setOnCheckedChangeListener { _, isChecked: Boolean ->
            binding.timeLayout.visibility = isChecked.toVisibility()
        }
        binding.insulinLabel.labelFor = binding.amount.editTextId
        binding.timeLabel.labelFor = binding.time.editTextId
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = SafeParse.stringToDouble(binding.amount.text)
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(ConstraintObject(insulin, aapsLogger)).value()
        val actions: LinkedList<String?> = LinkedList()
        val units = profileFunction.getUnits()
        val unitLabel = if (units == GlucoseUnit.MMOL) rh.gs(app.aaps.core.ui.R.string.mmol) else rh.gs(app.aaps.core.ui.R.string.mgdl)
        val recordOnlyChecked = binding.recordOnly.isChecked
        val eatingSoonChecked = binding.startEatingSoonTt.isChecked

        if (insulinAfterConstraints > 0) {
            actions.add(
                rh.gs(app.aaps.core.ui.R.string.bolus) + ": " + decimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump.pumpDescription.bolusStep)
                    .formatColor(context, rh, app.aaps.core.ui.R.attr.bolusColor)
            )
            if (recordOnlyChecked)
                actions.add(rh.gs(app.aaps.core.ui.R.string.bolus_recorded_only).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(
                    rh.gs(app.aaps.core.ui.R.string.bolus_constraint_applied_warn, insulin, insulinAfterConstraints).formatColor(context, rh, app.aaps.core.ui.R.attr.warningColor)
                )
        }
        val eatingSoonTTDuration = defaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = defaultValueHelper.determineEatingSoonTT()
        if (eatingSoonChecked)
            actions.add(
                rh.gs(R.string.temp_target_short) + ": " + (decimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + rh.gs(
                    app.aaps.core.ui.R.string.format_mins,
                    eatingSoonTTDuration
                ) + ")")
                    .formatColor(context, rh, app.aaps.core.ui.R.attr.tempTargetConfirmation)
            )

        val timeOffset = binding.time.value.toInt()
        val time = dateUtil.now() + T.mins(timeOffset.toLong()).msecs()
        if (timeOffset != 0)
            actions.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(time))

        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(rh.gs(app.aaps.core.ui.R.string.notes_label) + ": " + notes)

        if (insulinAfterConstraints > 0 || eatingSoonChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    if (eatingSoonChecked) {
                        uel.log(
                            UserEntry.Action.TT, UserEntry.Sources.InsulinDialog,
                            notes,
                            ValueWithUnit.TherapyEventTTReason(TemporaryTarget.Reason.EATING_SOON),
                            ValueWithUnit.fromGlucoseUnit(eatingSoonTT, units.asText),
                            ValueWithUnit.Minute(eatingSoonTTDuration)
                        )
                        disposable += repository.runTransactionForResult(
                            InsertAndCancelCurrentTemporaryTargetTransaction(
                                timestamp = System.currentTimeMillis(),
                                duration = TimeUnit.MINUTES.toMillis(eatingSoonTTDuration.toLong()),
                                reason = TemporaryTarget.Reason.EATING_SOON,
                                lowTarget = profileUtil.convertToMgdl(eatingSoonTT, profileFunction.getUnits()),
                                highTarget = profileUtil.convertToMgdl(eatingSoonTT, profileFunction.getUnits())
                            )
                        ).subscribe({ result ->
                                        result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                                        result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                    }, {
                                        aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                    })
                    }
                    if (insulinAfterConstraints > 0) {
                        val detailedBolusInfo = DetailedBolusInfo()
                        detailedBolusInfo.eventType = DetailedBolusInfo.EventType.CORRECTION_BOLUS
                        detailedBolusInfo.insulin = insulinAfterConstraints
                        detailedBolusInfo.context = context
                        detailedBolusInfo.notes = notes
                        detailedBolusInfo.timestamp = time
                        if (recordOnlyChecked) {
                            uel.log(UserEntry.Action.BOLUS, UserEntry.Sources.InsulinDialog,
                                    rh.gs(app.aaps.core.ui.R.string.record) + if (notes.isNotEmpty()) ": $notes" else "",
                                    ValueWithUnit.SimpleString(rh.gsNotLocalised(app.aaps.core.ui.R.string.record)),
                                    ValueWithUnit.Insulin(insulinAfterConstraints),
                                    ValueWithUnit.Minute(timeOffset).takeIf { timeOffset != 0 })
                            persistenceLayer.insertOrUpdateBolus(detailedBolusInfo.createBolus())
                            if (timeOffset == 0)
                                automation.removeAutomationEventBolusReminder()
                        } else {
                            uel.log(
                                UserEntry.Action.BOLUS, UserEntry.Sources.InsulinDialog,
                                notes,
                                ValueWithUnit.Insulin(insulinAfterConstraints)
                            )
                            commandQueue.bolus(detailedBolusInfo, object : Callback() {
                                override fun run() {
                                    if (!result.success) {
                                        uiInteraction.runAlarm(result.comment, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), app.aaps.core.ui.R.raw.boluserror)
                                    } else {
                                        automation.removeAutomationEventBolusReminder()
                                    }
                                }
                            })
                        }
                    }
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(app.aaps.core.ui.R.string.bolus), rh.gs(app.aaps.core.ui.R.string.no_action_selected))
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
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
    }
}