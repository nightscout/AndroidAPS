package info.nightscout.androidaps.plugins.treatments.fragments

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.events.EventTreatmentChange
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.dialogs.WizardInfoDialog
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsBolusFragment.RecyclerViewAdapter.TreatmentsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.treatments_bolus_fragment.*
import javax.inject.Inject

class TreatmentsBolusFragment : DaggerFragment() {
    private val disposable = CompositeDisposable()

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var sp: SP
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var treatmentsPlugin: TreatmentsPlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uploadQueue: UploadQueue
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_bolus_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        treatments_recyclerview.setHasFixedSize(true)
        treatments_recyclerview.layoutManager = LinearLayoutManager(view.context)
        treatments_recyclerview.adapter = RecyclerViewAdapter(treatmentsPlugin.treatmentsFromHistory)
        treatments_reshreshfromnightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.refresheventsfromnightscout) + "?", Runnable {
                    treatmentsPlugin.service.resetTreatments()
                    rxBus.send(EventNSClientRestart())
                })
            }
        }
        treatments_delete_future_treatments.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.overview_treatment_label), resourceHelper.gs(R.string.deletefuturetreatments) + "?", Runnable {
                    val futureTreatments = treatmentsPlugin.service.getTreatmentDataFromTime(DateUtil.now() + 1000, true)
                    for (treatment in futureTreatments) {
                        if (NSUpload.isIdValid(treatment._id))
                            nsUpload.removeCareportalEntryFromNS(treatment._id)
                        else
                            uploadQueue.removeID("dbAdd", treatment._id)
                        treatmentsPlugin.service.delete(treatment)
                    }
                    updateGui()
                })
            }
        }
        val nsUploadOnly = sp.getBoolean(R.string.key_ns_upload_only, true) || !buildHelper.isEngineeringMode()
        if (nsUploadOnly) treatments_reshreshfromnightscout.visibility = View.GONE
    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }) { fabricPrivacy.logException(it) }
        )
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ updateGui() }) { fabricPrivacy.logException(it) }
        )
        updateGui()
    }

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    inner class RecyclerViewAdapter internal constructor(var treatments: List<Treatment>) : RecyclerView.Adapter<TreatmentsViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TreatmentsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_bolus_item, viewGroup, false)
            return TreatmentsViewHolder(v)
        }

        override fun onBindViewHolder(holder: TreatmentsViewHolder, position: Int) {
            val profile = profileFunction.getProfile() ?: return
            val t = treatments[position]
            holder.date.text = dateUtil.dateAndTimeString(t.date)
            holder.insulin.text = resourceHelper.gs(R.string.formatinsulinunits, t.insulin)
            holder.carbs.text = resourceHelper.gs(R.string.format_carbs, t.carbs.toInt())
            val iob = t.iobCalc(System.currentTimeMillis(), profile.dia)
            holder.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.iobContrib)
            holder.mealOrCorrection.text = if (t.isSMB) "SMB" else if (t.mealBolus) resourceHelper.gs(R.string.mealbolus) else resourceHelper.gs(R.string.correctionbous)
            holder.ph.visibility = if (t.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.ns.visibility = if (NSUpload.isIdValid(t._id)) View.VISIBLE else View.GONE
            holder.invalid.visibility = if (t.isValid) View.GONE else View.VISIBLE
            if (iob.iobContrib != 0.0) holder.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.iob.setTextColor(holder.carbs.currentTextColor)
            if (t.date > DateUtil.now()) holder.date.setTextColor(resourceHelper.gc(R.color.colorScheduled)) else holder.date.setTextColor(holder.carbs.currentTextColor)
            holder.remove.tag = t
            holder.calculation.tag = t
            holder.calculation.visibility = if (t.getBoluscalc() == null) View.INVISIBLE else View.VISIBLE
        }

        override fun getItemCount(): Int {
            return treatments.size
        }

        inner class TreatmentsViewHolder internal constructor(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var cv: CardView = itemView.findViewById(R.id.treatments_cardview)
            var date: TextView = itemView.findViewById(R.id.treatments_date)
            var insulin: TextView = itemView.findViewById(R.id.treatments_insulin)
            var carbs: TextView = itemView.findViewById(R.id.treatments_carbs)
            var iob: TextView = itemView.findViewById(R.id.treatments_iob)
            var mealOrCorrection: TextView = itemView.findViewById(R.id.treatments_mealorcorrection)
            var remove: TextView = itemView.findViewById(R.id.treatments_remove)
            var calculation: TextView = itemView.findViewById(R.id.treatments_calculation)
            var ph: TextView = itemView.findViewById(R.id.pump_sign)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)
            var invalid: TextView = itemView.findViewById(R.id.invalid_sign)

            init {
                calculation.setOnClickListener {
                    val treatment = it.tag as Treatment
                    if (treatment.getBoluscalc() != null) {
                        val wizardDialog = WizardInfoDialog()
                        wizardDialog.setData(treatment.getBoluscalc()!!)
                        wizardDialog.show(childFragmentManager, "WizardInfoDialog")
                    }
                }
                calculation.paintFlags = calculation.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                remove.setOnClickListener {
                    val treatment = it.tag as Treatment
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.configbuilder_insulin) + ": " +
                            resourceHelper.gs(R.string.formatinsulinunits, treatment.insulin) + "\n" +
                            resourceHelper.gs(R.string.carbs) + ": " + resourceHelper.gs(R.string.format_carbs, treatment.carbs.toInt()) + "\n" +
                            resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(treatment.date)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            if (treatment.source == Source.PUMP) {
                                treatment.isValid = false
                                treatmentsPlugin.service.update(treatment)
                            } else {
                                if (NSUpload.isIdValid(treatment._id))
                                    nsUpload.removeCareportalEntryFromNS(treatment._id)
                                else
                                    uploadQueue.removeID("dbAdd", treatment._id)
                                treatmentsPlugin.service.delete(treatment)
                            }
                            updateGui()
                        })
                    }
                }
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }

    private fun updateGui() {
        treatments_recyclerview?.swapAdapter(RecyclerViewAdapter(treatmentsPlugin.treatmentsFromHistory), false)
        if (treatmentsPlugin.lastCalculationTreatments != null) {
            treatments_iobtotal?.text = resourceHelper.gs(R.string.formatinsulinunits, treatmentsPlugin.lastCalculationTreatments.iob)
            treatments_iobactivitytotal?.text = resourceHelper.gs(R.string.formatinsulinunits, treatmentsPlugin.lastCalculationTreatments.activity)
        }
        if (treatmentsPlugin.service.getTreatmentDataFromTime(DateUtil.now() + 1000, true).isNotEmpty())
            treatments_delete_future_treatments?.visibility = View.VISIBLE
        else
            treatments_delete_future_treatments?.visibility = View.GONE
    }
}