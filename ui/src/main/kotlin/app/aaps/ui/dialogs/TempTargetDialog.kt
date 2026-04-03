package app.aaps.ui.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.lifecycle.lifecycleScope
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.TT
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogTemptargetBinding
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.text.DecimalFormat
import java.util.LinkedList
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TempTargetDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var ctx: Context
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction

    private lateinit var reasonList: List<String>

    private var queryingProtection = false
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
                ?: 60.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, binding.okcancel.ok
        )

        if (profileUtil.units == GlucoseUnit.MMOL)
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("tempTarget")
                    ?: 5.6,
                Constants.MIN_TT_MMOL, Constants.MAX_TT_MMOL, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok
            )
        else
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("tempTarget")
                    ?: 101.0,
                Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1.0, DecimalFormat("0"), false, binding.okcancel.ok
            )

        val units = profileUtil.units
        binding.units.text = if (units == GlucoseUnit.MMOL) rh.gs(app.aaps.core.ui.R.string.mmol) else rh.gs(app.aaps.core.ui.R.string.mgdl)

        // temp target
        context?.let { context ->
            binding.targetCancel.visibility = (runBlocking { persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now()) } != null).toVisibility()

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
                binding.temptarget.value = preferences.get(UnitDoubleKey.OverviewEatingSoonTarget)
                binding.duration.value = preferences.get(IntKey.OverviewEatingSoonDuration).toDouble()
                binding.reasonList.setText(rh.gs(app.aaps.core.ui.R.string.eatingsoon), false)
            }

            R.id.activity    -> {
                binding.temptarget.value = preferences.get(UnitDoubleKey.OverviewActivityTarget)
                binding.duration.value = preferences.get(IntKey.OverviewActivityDuration).toDouble()
                binding.reasonList.setText(rh.gs(app.aaps.core.ui.R.string.activity), false)
            }

            R.id.hypo        -> {
                binding.temptarget.value = preferences.get(UnitDoubleKey.OverviewHypoTarget)
                binding.duration.value = preferences.get(IntKey.OverviewHypoDuration).toDouble()
                binding.reasonList.setText(rh.gs(app.aaps.core.ui.R.string.hypo), false)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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

        uiInteraction.showOkCancelDialog(
            context = requireActivity(),
            title = rh.gs(app.aaps.core.ui.R.string.temporary_target),
            message = Joiner.on("<br/>").join(actions),
            ok = {
                val units = profileFunction.getUnits()
                val listValues = when (reason) {
                    rh.gs(app.aaps.core.ui.R.string.eatingsoon) -> listOf(
                        ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                        ValueWithUnit.TETTReason(TT.Reason.EATING_SOON),
                        ValueWithUnit.fromGlucoseUnit(target, units),
                        ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.activity) -> listOf(
                        ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                        ValueWithUnit.TETTReason(TT.Reason.ACTIVITY),
                        ValueWithUnit.fromGlucoseUnit(target, units),
                        ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.hypo) -> listOf(
                        ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                        ValueWithUnit.TETTReason(TT.Reason.HYPOGLYCEMIA),
                        ValueWithUnit.fromGlucoseUnit(target, units),
                        ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.manual) -> listOf(
                        ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                        ValueWithUnit.TETTReason(TT.Reason.CUSTOM),
                        ValueWithUnit.fromGlucoseUnit(target, units),
                        ValueWithUnit.Minute(duration)
                    )

                    rh.gs(app.aaps.core.ui.R.string.stoptemptarget) -> listOf(ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged })

                    else -> listOf()
                }
                if (target == 0.0 || duration == 0) {
                    lifecycleScope.launch {
                        persistenceLayer.cancelCurrentTemporaryTargetIfAny(
                            timestamp = eventTime,
                            action = Action.TT,
                            source = Sources.TTDialog,
                            note = null,
                            listValues = listOf()
                        )
                    }
                } else {
                    lifecycleScope.launch {
                        persistenceLayer.insertAndCancelCurrentTemporaryTarget(
                            TT(
                                timestamp = eventTime,
                                duration = TimeUnit.MINUTES.toMillis(duration.toLong()),
                                reason = when (reason) {
                                    rh.gs(app.aaps.core.ui.R.string.eatingsoon) -> TT.Reason.EATING_SOON
                                    rh.gs(app.aaps.core.ui.R.string.activity)   -> TT.Reason.ACTIVITY
                                    rh.gs(app.aaps.core.ui.R.string.hypo)       -> TT.Reason.HYPOGLYCEMIA
                                    else                                        -> TT.Reason.CUSTOM
                                },
                                lowTarget = profileUtil.convertToMgdl(target, profileFunction.getUnits()),
                                highTarget = profileUtil.convertToMgdl(target, profileFunction.getUnits())
                            ),
                            action = Action.TT,
                            source = Sources.TTDialog,
                            note = null,
                            listValues = listValues.filterNotNull()
                        )
                    }
                }

                if (duration == 10) preferences.put(BooleanNonKey.ObjectivesTempTargetUsed, true)
            }
        )
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