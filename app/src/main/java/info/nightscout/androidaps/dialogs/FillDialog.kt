package info.nightscout.androidaps.dialogs

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.entities.TherapyEvent
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.transactions.InsertIfNewByTimestampTherapyEventTransaction
import info.nightscout.androidaps.databinding.DialogFillBinding
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class FillDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var ctx: Context
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private val disposable = CompositeDisposable()

    private var _binding: DialogFillBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("fill_insulin_amount", binding.fillInsulinamount.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        onCreateViewGeneral()
        _binding = DialogFillBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        binding.fillInsulinamount.setParams(savedInstanceState?.getDouble("fill_insulin_amount")
            ?: 0.0, 0.0, maxInsulin, bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), true, binding.okcancel.ok)
        val amount1 = sp.getDouble("fill_button1", 0.3)
        if (amount1 > 0) {
            binding.fillPresetButton1.visibility = View.VISIBLE
            binding.fillPresetButton1.text = DecimalFormatter.toPumpSupportedBolus(amount1, activePlugin.activePump) // + "U");
            binding.fillPresetButton1.setOnClickListener { binding.fillInsulinamount.value = amount1 }
        } else {
            binding.fillPresetButton1.visibility = View.GONE
        }
        val amount2 = sp.getDouble("fill_button2", 0.0)
        if (amount2 > 0) {
            binding.fillPresetButton2.visibility = View.VISIBLE
            binding.fillPresetButton2.text = DecimalFormatter.toPumpSupportedBolus(amount2, activePlugin.activePump) // + "U");
            binding.fillPresetButton2.setOnClickListener { binding.fillInsulinamount.value = amount2 }
        } else {
            binding.fillPresetButton2.visibility = View.GONE
        }
        val amount3 = sp.getDouble("fill_button3", 0.0)
        if (amount3 > 0) {
            binding.fillPresetButton3.visibility = View.VISIBLE
            binding.fillPresetButton3.text = DecimalFormatter.toPumpSupportedBolus(amount3, activePlugin.activePump) // + "U");
            binding.fillPresetButton3.setOnClickListener { binding.fillInsulinamount.value = amount3 }
        } else {
            binding.fillPresetButton3.visibility = View.GONE
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false
        val insulin = SafeParse.stringToDouble(binding.fillInsulinamount.text ?: return false)
        val actions: LinkedList<String?> = LinkedList()

        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        if (insulinAfterConstraints > 0) {
            actions.add(resourceHelper.gs(R.string.fillwarning))
            actions.add("")
            actions.add(resourceHelper.gs(R.string.bolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, resourceHelper).formatColor(resourceHelper, R.color.colorInsulinButton))
            if (abs(insulinAfterConstraints - insulin) > 0.01)
                actions.add(resourceHelper.gs(R.string.bolusconstraintappliedwarn, insulin, insulinAfterConstraints).formatColor(resourceHelper, R.color.warning))
        }
        val siteChange = binding.fillCatheterChange.isChecked
        if (siteChange)
            actions.add(resourceHelper.gs(R.string.record_pump_site_change).formatColor(resourceHelper, R.color.actionsConfirm))
        val insulinChange = binding.fillCartridgeChange.isChecked
        if (insulinChange)
            actions.add(resourceHelper.gs(R.string.record_insulin_cartridge_change).formatColor(resourceHelper, R.color.actionsConfirm))
        val notes: String = binding.notesLayout.notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.notes_label) + ": " + notes)
        eventTime -= eventTime % 1000

        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        if (insulinAfterConstraints > 0 || binding.fillCatheterChange.isChecked || binding.fillCartridgeChange.isChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.primefill), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                    if (insulinAfterConstraints > 0) {
                        uel.log(Action.PRIME_BOLUS, Sources.FillDialog,
                            notes,
                            ValueWithUnit.Insulin(insulinAfterConstraints).takeIf { insulinAfterConstraints != 0.0 })
                        requestPrimeBolus(insulinAfterConstraints, notes)
                    }
                    if (siteChange) {
                        uel.log(Action.SITE_CHANGE, Sources.FillDialog,
                            notes,
                            ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                            ValueWithUnit.TherapyEventType(TherapyEvent.Type.CANNULA_CHANGE))
                        disposable += repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(
                            timestamp = eventTime,
                            type = TherapyEvent.Type.CANNULA_CHANGE,
                            note = notes,
                            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                        )).subscribe(
                            { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted therapy event $it") } },
                            { aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it) }
                        )
                    }
                    if (insulinChange) {
                        // add a second for case of both checked
                        uel.log(Action.RESERVOIR_CHANGE, Sources.FillDialog,
                            notes,
                            ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                            ValueWithUnit.TherapyEventType(TherapyEvent.Type.INSULIN_CHANGE))
                        disposable += repository.runTransactionForResult(InsertIfNewByTimestampTherapyEventTransaction(
                            timestamp = eventTime + 1000,
                            type = TherapyEvent.Type.INSULIN_CHANGE,
                            note = notes,
                            glucoseUnit = TherapyEvent.GlucoseUnit.MGDL
                        )).subscribe(
                            { result -> result.inserted.forEach { aapsLogger.debug(LTag.DATABASE, "Inserted therapy event $it") } },
                            { aapsLogger.error(LTag.DATABASE, "Error while saving therapy event", it) }
                        )
                    }
                }, null)
            }
        } else {
            activity?.let { activity ->
                OKDialog.show(activity, resourceHelper.gs(R.string.primefill), resourceHelper.gs(R.string.no_action_selected))
            }
        }
        dismiss()
        return true
    }

    private fun requestPrimeBolus(insulin: Double, notes: String) {
        val detailedBolusInfo = DetailedBolusInfo()
        detailedBolusInfo.insulin = insulin
        detailedBolusInfo.context = context
        detailedBolusInfo.bolusType = DetailedBolusInfo.BolusType.PRIMING
        detailedBolusInfo.notes = notes
        commandQueue.bolus(detailedBolusInfo, object : Callback() {
            override fun run() {
                if (!result.success) {
                    ErrorHelperActivity.runAlarm(ctx, result.comment, resourceHelper.gs(R.string.treatmentdeliveryerror), R.raw.boluserror)
                }
            }
        })
    }
}
