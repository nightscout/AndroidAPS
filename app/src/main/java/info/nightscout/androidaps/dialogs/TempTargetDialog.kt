package info.nightscout.androidaps.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import com.google.common.base.Joiner
import com.google.common.collect.Lists
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.databinding.DialogTemptargetBinding
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import java.text.DecimalFormat
import java.util.*
import javax.inject.Inject

class TempTargetDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var defaultValueHelper: DefaultValueHelper
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var activePlugin: ActivePluginProvider

    lateinit var reasonList: List<String>

    private var _binding: DialogTemptargetBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("duration", binding.duration.value)
        savedInstanceState.putDouble("temptarget", binding.temptarget.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogTemptargetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.duration.setParams(savedInstanceState?.getDouble("duration")
            ?: 0.0, 0.0, Constants.MAX_PROFILE_SWITCH_DURATION, 10.0, DecimalFormat("0"), false, binding.okcancel.ok)

        if (profileFunction.getUnits() == Constants.MMOL)
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("temptarget")
                    ?: 8.0,
                Constants.MIN_TT_MMOL, Constants.MAX_TT_MMOL, 0.1, DecimalFormat("0.0"), false, binding.okcancel.ok)
        else
            binding.temptarget.setParams(
                savedInstanceState?.getDouble("temptarget")
                    ?: 144.0,
                Constants.MIN_TT_MGDL, Constants.MAX_TT_MGDL, 1.0, DecimalFormat("0"), false, binding.okcancel.ok)

        val units = profileFunction.getUnits()
        binding.units.text = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)

        // temp target
        context?.let { context ->
            if (activePlugin.activeTreatments.tempTargetFromHistory != null)
                binding.targetCancel.visibility = View.VISIBLE
            else
                binding.targetCancel.visibility = View.GONE

            reasonList = Lists.newArrayList(
                resourceHelper.gs(R.string.manual),
                resourceHelper.gs(R.string.eatingsoon),
                resourceHelper.gs(R.string.activity),
                resourceHelper.gs(R.string.hypo)
            )
            val adapterReason = ArrayAdapter(context, R.layout.spinner_centered, reasonList)
            binding.reason.adapter = adapterReason

            binding.targetCancel.setOnClickListener { shortClick(it) }
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
                binding.reason.setSelection(reasonList.indexOf(resourceHelper.gs(R.string.eatingsoon)))
            }

            R.id.activity -> {
                binding.temptarget.value = defaultValueHelper.determineActivityTT()
                binding.duration.value = defaultValueHelper.determineActivityTTDuration().toDouble()
                binding.reason.setSelection(reasonList.indexOf(resourceHelper.gs(R.string.activity)))
            }

            R.id.hypo -> {
                binding.temptarget.value = defaultValueHelper.determineHypoTT()
                binding.duration.value = defaultValueHelper.determineHypoTTDuration().toDouble()
                binding.reason.setSelection(reasonList.indexOf(resourceHelper.gs(R.string.hypo)))
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
        val reason = binding.reason.selectedItem?.toString() ?: return false
        val unitResId = if (profileFunction.getUnits() == Constants.MGDL) R.string.mgdl else R.string.mmol
        val target = binding.temptarget.value
        val duration = binding.duration.value.toInt()
        if (target != 0.0 && duration != 0) {
            actions.add(resourceHelper.gs(R.string.reason) + ": " + reason)
            actions.add(resourceHelper.gs(R.string.target_label) + ": " + Profile.toCurrentUnitsString(profileFunction, target) + " " + resourceHelper.gs(unitResId))
            actions.add(resourceHelper.gs(R.string.duration) + ": " + resourceHelper.gs(R.string.format_mins, duration))
        } else {
            actions.add(resourceHelper.gs(R.string.stoptemptarget))
        }
        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        activity?.let { activity ->
            OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.careportal_temporarytarget), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                aapsLogger.debug("USER ENTRY: TEMP TARGET $target duration: $duration")
                if (target == 0.0 || duration == 0) {
                    val tempTarget = TempTarget()
                        .date(eventTime)
                        .duration(0)
                        .low(0.0).high(0.0)
                        .source(Source.USER)
                    treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                } else {
                    val tempTarget = TempTarget()
                        .date(eventTime)
                        .duration(duration)
                        .reason(reason)
                        .source(Source.USER)
                        .low(Profile.toMgdl(target, profileFunction.getUnits()))
                        .high(Profile.toMgdl(target, profileFunction.getUnits()))
                    treatmentsPlugin.addToHistoryTempTarget(tempTarget)
                }
                if (duration == 10) sp.putBoolean(R.string.key_objectiveusetemptarget, true)
            })
        }
        return true
    }
}
