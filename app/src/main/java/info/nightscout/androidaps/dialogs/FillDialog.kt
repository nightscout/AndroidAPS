package info.nightscout.androidaps.dialogs

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.common.base.Joiner
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.ErrorHelperActivity
import info.nightscout.androidaps.data.DetailedBolusInfo
import info.nightscout.androidaps.db.CareportalEvent
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.CommandQueueProvider
import info.nightscout.androidaps.interfaces.Constraint
import info.nightscout.androidaps.plugins.configBuilder.ConstraintChecker
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.queue.Callback
import info.nightscout.androidaps.utils.DecimalFormatter
import info.nightscout.androidaps.utils.HtmlHelper
import info.nightscout.androidaps.utils.SafeParse
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.extensions.formatColor
import info.nightscout.androidaps.utils.resources.ResourceHelper
import kotlinx.android.synthetic.main.dialog_fill.*
import kotlinx.android.synthetic.main.notes.*
import kotlinx.android.synthetic.main.okcancel.*
import java.util.*
import javax.inject.Inject
import kotlin.math.abs

class FillDialog : DialogFragmentWithDate() {
    @Inject lateinit var constraintChecker: ConstraintChecker
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var ctx: Context
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var commandQueue: CommandQueueProvider
    @Inject lateinit var activePlugin: ActivePluginProvider

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putDouble("fill_insulin_amount", fill_insulinamount.value)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        onCreateViewGeneral()
        return inflater.inflate(R.layout.dialog_fill, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val maxInsulin = constraintChecker.getMaxBolusAllowed().value()
        val bolusStep = activePlugin.activePump.pumpDescription.bolusStep
        fill_insulinamount.setParams(savedInstanceState?.getDouble("fill_insulin_amount")
            ?: 0.0, 0.0, maxInsulin, bolusStep, DecimalFormatter.pumpSupportedBolusFormat(activePlugin.activePump), true, ok)
        val amount1 = sp.getDouble("fill_button1", 0.3)
        if (amount1 > 0) {
            fill_preset_button1.visibility = View.VISIBLE
            fill_preset_button1.text = DecimalFormatter.toPumpSupportedBolus(amount1, activePlugin.activePump) // + "U");
            fill_preset_button1.setOnClickListener { fill_insulinamount.value = amount1 }
        } else {
            fill_preset_button1.visibility = View.GONE
        }
        val amount2 = sp.getDouble("fill_button2", 0.0)
        if (amount2 > 0) {
            fill_preset_button2.visibility = View.VISIBLE
            fill_preset_button2.text = DecimalFormatter.toPumpSupportedBolus(amount2, activePlugin.activePump) // + "U");
            fill_preset_button2.setOnClickListener { fill_insulinamount.value = amount2 }
        } else {
            fill_preset_button2.visibility = View.GONE
        }
        val amount3 = sp.getDouble("fill_button3", 0.0)
        if (amount3 > 0) {
            fill_preset_button3.visibility = View.VISIBLE
            fill_preset_button3.text = DecimalFormatter.toPumpSupportedBolus(amount3, activePlugin.activePump) // + "U");
            fill_preset_button3.setOnClickListener { fill_insulinamount.value = amount3 }
        } else {
            fill_preset_button3.visibility = View.GONE
        }

    }

    override fun submit(): Boolean {
        val insulin = SafeParse.stringToDouble(fill_insulinamount?.text ?: return false)
        val actions: LinkedList<String?> = LinkedList()

        val insulinAfterConstraints = constraintChecker.applyBolusConstraints(Constraint(insulin)).value()
        if (insulinAfterConstraints > 0) {
            actions.add(resourceHelper.gs(R.string.fillwarning))
            actions.add("")
            actions.add(resourceHelper.gs(R.string.bolus) + ": " + DecimalFormatter.toPumpSupportedBolus(insulinAfterConstraints, activePlugin.activePump, resourceHelper).formatColor(resourceHelper, R.color.colorInsulinButton))
            if (abs(insulinAfterConstraints - insulin) > 0.01)
                actions.add(resourceHelper.gs(R.string.bolusconstraintappliedwarn, insulin, insulinAfterConstraints).formatColor(resourceHelper, R.color.warning))
        }
        val siteChange = fill_catheter_change.isChecked
        if (siteChange)
            actions.add(resourceHelper.gs(R.string.record_pump_site_change).formatColor(resourceHelper, R.color.actionsConfirm))
        val insulinChange = fill_cartridge_change.isChecked
        if (insulinChange)
            actions.add(resourceHelper.gs(R.string.record_insulin_cartridge_change).formatColor(resourceHelper, R.color.actionsConfirm))
        val notes = notes.text.toString()
        if (notes.isNotEmpty())
            actions.add(resourceHelper.gs(R.string.careportal_newnstreatment_notes_label) + ": " + notes)
        eventTime -= eventTime % 1000

        if (eventTimeChanged)
            actions.add(resourceHelper.gs(R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))

        if (insulinAfterConstraints > 0 || fill_catheter_change.isChecked || fill_cartridge_change.isChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.primefill), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), Runnable {
                    if (insulinAfterConstraints > 0) {
                        aapsLogger.debug("USER ENTRY: PRIME BOLUS $insulinAfterConstraints")
                        requestPrimeBolus(insulinAfterConstraints, notes)
                    }
                    if (siteChange) {
                        aapsLogger.debug("USER ENTRY: SITE CHANGE")
                        nsUpload.generateCareportalEvent(CareportalEvent.SITECHANGE, eventTime, notes)
                    }
                    if (insulinChange) {
                        // add a second for case of both checked
                        aapsLogger.debug("USER ENTRY: INSULIN CHANGE")
                        nsUpload.generateCareportalEvent(CareportalEvent.INSULINCHANGE, eventTime + 1000, notes)
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
        detailedBolusInfo.source = Source.USER
        detailedBolusInfo.isValid = false // do not count it in IOB (for pump history)
        detailedBolusInfo.notes = notes
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
