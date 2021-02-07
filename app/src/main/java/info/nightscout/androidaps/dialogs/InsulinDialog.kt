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
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.databinding.DialogInsulinBinding
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TempTarget
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.formatColor
import info.nightscout.androidaps.utils.extensions.toSignedString
import info.nightscout.androidaps.utils.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
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
    @Inject lateinit var config: Config

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

    private var _binding: DialogInsulinBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    private fun validateInputs() {
        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        if (abs(binding.time.value.toInt()) > 12 * 60) {
            binding.time.value = 0.0
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.constraintapllied))
        }
        if (binding.amount.value > maxInsulin) {
            binding.amount.value = 0.0
            ToastUtils.showToastInUiThread(context, resourceHelper.gs(R.string.bolusconstraintapplied))
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("time", binding.time.value)
        savedInstanceState.putDouble("amount", binding.amount.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
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

        binding.time.setParams(savedInstanceState?.getDouble("time")
            ?: 0.0, -12 * 60.0, 12 * 60.0, 5.0, DecimalFormat("0"), false, binding.okcancel.ok, textWatcher)
        binding.amount.setParams(savedInstanceState?.getDouble("amount")
            ?: 0.0, 0.0, maxInsulin, activePlugin.activePump.pumpDescription.bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), false, binding.okcancel.ok, textWatcher)

        binding.plus05.text = sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT).toSignedString(activePlugin.activePump)
        binding.plus05.setOnClickListener {
            binding.amount.value = max(0.0, binding.amount.value
                + sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_1), PLUS1_DEFAULT))
            validateInputs()
        }
        binding.plus10.text = sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT).toSignedString(activePlugin.activePump)
        binding.plus10.setOnClickListener {
            binding.amount.value = max(0.0, binding.amount.value
                + sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_2), PLUS2_DEFAULT))
            validateInputs()
        }
        binding.plus20.text = sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT).toSignedString(activePlugin.activePump)
        binding.plus20.setOnClickListener {
            binding.amount.value = max(0.0, binding.amount.value
                + sp.getDouble(resourceHelper.gs(R.string.key_insulin_button_increment_3), PLUS3_DEFAULT))
            validateInputs()
        }

        binding.timeLayout.visibility = View.GONE
        binding.recordOnly.setOnCheckedChangeListener { _, isChecked: Boolean ->
            binding.timeLayout.visibility = isChecked.toVisibility()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val pumpDescription = activePlugin.activePump.pumpDescription
        val insulin = SafeParse.stringToDouble(binding.amount.text ?: return false)
        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        val actions: LinkedList<String?> = LinkedList()
        val units = profileFunction.getUnits()
        val unitLabel = if (units == Constants.MMOL) resourceHelper.gs(R.string.mmol) else resourceHelper.gs(R.string.mgdl)
        val recordOnlyChecked = binding.recordOnly.isChecked
        val eatingSoonChecked = binding.startEatingSoonTt.isChecked

        if (insulinAfterConstraints > 0) {
            actions.add(resourceHelper.gs(R.string.bolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, resourceHelper).formatColor(resourceHelper, R.color.bolus))
            if (recordOnlyChecked)
                actions.add(resourceHelper.gs(R.string.bolusrecordedonly).formatColor(resourceHelper, R.color.warning))
            if (abs(insulinAfterConstraints - insulin) > pumpDescription.pumpType.determineCorrectBolusStepSize(insulinAfterConstraints))
                actions.add(resourceHelper.gs(R.string.bolusconstraintappliedwarn, insulin, insulinAfterConstraints).formatColor(resourceHelper, R.color.warning))
        }
        val eatingSoonTTDuration = defaultValueHelper.determineEatingSoonTTDuration()
        val eatingSoonTT = defaultValueHelper.determineEatingSoonTT()
        if (eatingSoonChecked)
            actions.add(resourceHelper.gs(R.string.temptargetshort) + ": " + (DecimalFormatter.to1Decimal(eatingSoonTT) + " " + unitLabel + " (" + resourceHelper.gs(R.string.format_mins, eatingSoonTTDuration) + ")").formatColor(resourceHelper, R.color.tempTargetConfirmation))

        val timeOffset = binding.time.value.toInt()
        val time = DateUtil.now() + T.mins(timeOffset.toLong()).msecs()
        if (timeOffset != 0)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(time))

        val notes = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)

        if (insulinAfterConstraints > 0 || eatingSoonChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.bolus), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
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
                })
            }
        } else
            activity?.let { activity ->
                OKDialog.show(activity, resourceHelper.gs(R.string.bolus), resourceHelper.gs(R.string.no_action_selected))
            }
        return true
    }
}