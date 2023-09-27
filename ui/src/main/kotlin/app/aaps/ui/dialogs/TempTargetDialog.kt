package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import app.aaps.core.interfaces.configuration.Constants
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.GlucoseUnit
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.DefaultValueHelper
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.core.utils.HtmlHelper
import app.aaps.database.ValueWrapper
import app.aaps.database.entities.TemporaryTarget
import app.aaps.database.entities.UserEntry
import app.aaps.database.entities.ValueWithUnit
import app.aaps.database.impl.AppRepository
import app.aaps.database.impl.transactions.CancelCurrentTemporaryTargetIfAnyTransaction
import app.aaps.database.impl.transactions.InsertAndCancelCurrentTemporaryTargetTransaction
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogTemptargetBinding
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.text.DecimalFormat
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TempTargetDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck

    private lateinit var reasonList: List<String>

    private var queryingProtection = false
    private val disposable = CompositeDisposable()
    private var _binding: DialogTemptargetBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putDouble("tempTarget", binding.temptarget.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogTemptargetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.duration.setParams(
            savedInstanceState?.getDouble("duration")
                ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, binding.okcancel.ok
        )

        if (profileUtil.units == GlucoseUnit.MMOL)
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("tempTarget")
                    ?: 8.0,
                Constants.MIN_TT_MMOL, Constants.MAX_TT_MMOL, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok
            )
        else
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("tempTarget")
                    ?: 144.0,
                Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1.0, DecimalFormat("0"), false, binding.okcancel.ok
            )

        val units = profileUtil.units
        binding.units.text = if (units == GlucoseUnit.MMOL) rh.gs(app.aaps.core.ui.R.string.mmol) else rh.gs(app.aaps.core.ui.R.string.mgdl)

        // temp target
        context?.let { context ->
            if (repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet() is ValueWrapper.Existing)
                binding.targetCancel.visibility = View.VISIBLE
            else
                binding.targetCancel.visibility = View.GONE

            reasonList = Lists.newArrayList(
                rh.gs(app.aaps.core.ui.R.string.manual),
                rh.gs(app.aaps.core.ui.R.string.eatingsoon),
                rh.gs(app.aaps.core.ui.R.string.activity),
                rh.gs(app.aaps.core.ui.R.string.hypo)
            )
            binding.reasonList.setAdapter(ArrayAdapter(context, app.aaps.core.ui.R.layout.spinner_centered, reasonList))

            binding.targetCancel.setOnClickListener { binding.duration.value = 0.0; shortClick(it) }
            binding.eatingSoon.setOnClickListener { shortClick(it) }
            binding.activity.setOnClickListener { shortClick(it) }
            binding.hypo.setOnClickListener { shortClick(it) }

            binding.eatingSoon.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
            binding.activity.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
            binding.hypo.setOnLongClickListener {
                longClick(it)
                return@setOnLongClickListener true
            }
            binding.durationLabel.labelFor = binding.duration.editTextId
            binding.temptargetLabel.labelFor = binding.temptarget.editTextId
        }
    }

    private fun shortClick(v: View) {
        v.performLongClick()
        if (submit()) dismiss()
    }

    private fun longClick(v: View) {
        when (v.id) {
            R.id.eating_soon -> {
                binding.temptarget.value = defaultValueHelper.determineEatingSoonTT()
                binding.duration.value = defaultValueHelper.determineEatingSoonTTDuration().toDouble()
                binding.reasonList.setText(rh.gs(app.aaps.core.ui.R.string.eatingsoon), false)
            }

            R.id.activity    -> {
                binding.temptarget.value = defaultValueHelper.determineActivityTT()
                binding.duration.value = defaultValueHelper.determineActivityTTDuration().toDouble()
                binding.reasonList.setText(rh.gs(app.aaps.core.ui.R.string.activity), false)
            }

            R.id.hypo        -> {
                binding.temptarget.value = defaultValueHelper.determineHypoTT()
                binding.duration.value = defaultValueHelper.determineHypoTTDuration().toDouble()
                binding.reasonList.setText(rh.gs(app.aaps.core.ui.R.string.hypo), false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val actions: LinkedList<String> = LinkedList()
        var reason = binding.reasonList.text.toString()
        val unitResId = if (profileFunction.getUnits() == GlucoseUnit.MGDL) app.aaps.core.ui.R.string.mgdl else app.aaps.core.ui.R.string.mmol
        val target = binding.temptarget.value
        val duration = binding.duration.value.toInt()
        if (target != 0.0 && duration != 0) {
            actions.add(rh.gs(app.aaps.core.ui.R.string.reason) + ": " + reason)
            actions.add(rh.gs(app.aaps.core.ui.R.string.target_label) + ": " + profileUtil.stringInCurrentUnitsDetect(target) + " " + rh.gs(unitResId))
            actions.add(rh.gs(app.aaps.core.ui.R.string.duration) + ": " + rh.gs(app.aaps.core.ui.R.string.format_mins, duration))
        } else {
            actions.add(rh.gs(app.aaps.core.ui.R.string.stoptemptarget))
            reason = rh.gs(app.aaps.core.ui.R.string.stoptemptarget)
        }
        if (eventTimeChanged)
            actions.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.temporary_target), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                val units = profileFunction.getUnits()
                when (reason) {
                    rh.gs(app.aaps.core.ui.R.string.eatingsoon) -> uel.log(
                        UserEntry.Action.TT, UserEntry.Sources.TTDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged }, ValueWithUnit.TherapyEventTTReason(
                            TemporaryTarget.Reason.EATING_SOON
                        ), ValueWithUnit.fromGlucoseUnit(target, units.asText), ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.activity) -> uel.log(
                        UserEntry.Action.TT, UserEntry.Sources.TTDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged }, ValueWithUnit.TherapyEventTTReason(
                            TemporaryTarget.Reason.ACTIVITY
                        ), ValueWithUnit.fromGlucoseUnit(target, units.asText), ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.hypo) -> uel.log(
                        UserEntry.Action.TT, UserEntry.Sources.TTDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged }, ValueWithUnit.TherapyEventTTReason(
                            TemporaryTarget.Reason.HYPOGLYCEMIA
                        ), ValueWithUnit.fromGlucoseUnit(target, units.asText), ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.manual) -> uel.log(
                        UserEntry.Action.TT, UserEntry.Sources.TTDialog, ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged }, ValueWithUnit.TherapyEventTTReason(
                            TemporaryTarget.Reason.CUSTOM
                        ), ValueWithUnit.fromGlucoseUnit(target, units.asText), ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.stoptemptarget) -> uel.log(
                        UserEntry.Action.CANCEL_TT,
                        UserEntry.Sources.TTDialog,
                        ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged })
                }
                if (target == 0.0 || duration == 0) {
                    disposable += repository.runTransactionForResult(CancelCurrentTemporaryTargetIfAnyTransaction(eventTime))
                        .subscribe({ result ->
                                       result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                   }, {
                                       aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                   })
                } else {
                    disposable += repository.runTransactionForResult(
                        InsertAndCancelCurrentTemporaryTargetTransaction(
                            timestamp = eventTime,
                            duration = TimeUnit.MINUTES.toMillis(duration.toLong()),
                            reason = when (reason) {
                                rh.gs(app.aaps.core.ui.R.string.eatingsoon) -> TemporaryTarget.Reason.EATING_SOON
                                rh.gs(app.aaps.core.ui.R.string.activity)   -> TemporaryTarget.Reason.ACTIVITY
                                rh.gs(app.aaps.core.ui.R.string.hypo)       -> TemporaryTarget.Reason.HYPOGLYCEMIA
                                else                                        -> TemporaryTarget.Reason.CUSTOM
                            },
                            lowTarget = profileUtil.convertToMgdl(target, profileFunction.getUnits()),
                            highTarget = profileUtil.convertToMgdl(target, profileFunction.getUnits())
                        )
                    ).subscribe({ result ->
                                    result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted temp target $it") }
                                    result.updated.forEach { aapsLogger.debug(LTag.DATABASE, "Updated temp target $it") }
                                }, {
                                    aapsLogger.error(LTag.DATABASE, "Error while saving temporary target", it)
                                })
                }

                if (duration == 10) sp.putBoolean(app.aaps.core.utils.R.string.key_objectiveusetemptarget, true)
            })
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