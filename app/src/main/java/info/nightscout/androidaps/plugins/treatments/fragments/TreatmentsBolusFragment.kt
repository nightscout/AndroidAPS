package info.nightscout.androidaps.plugins.treatments.fragments

import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.databinding.TreatmentsBolusFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsBolusItemBinding
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.Treatment
import info.nightscout.androidaps.dialogs.WizardInfoDialog
import info.nightscout.androidaps.events.EventTreatmentChange
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.TreatmentsPlugin
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsBolusFragment.RecyclerViewAdapter.TreatmentsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
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
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsBolusFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsBolusFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = RecyclerViewAdapter(treatmentsPlugin.treatmentsFromHistory)
        binding.refreshFromNightscout.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.refresheventsfromnightscout) + "?") {
                    uel.log("TREAT NS REFRESH")
                    treatmentsPlugin.service.resetTreatments()
                    rxBus.send(EventNSClientRestart())
                }
            }
        }
        binding.deleteFutureTreatments.setOnClickListener {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.overview_treatment_label), resourceHelper.gs(R.string.deletefuturetreatments) + "?", Runnable {
                    uel.log("DELETE FUTURE TREATMENTS")
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
        if (nsUploadOnly) binding.refreshFromNightscout.visibility = View.GONE
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ updateGui() }, fabricPrivacy::logException)
        )
        updateGui()
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class RecyclerViewAdapter internal constructor(var treatments: List<Treatment>) : RecyclerView.Adapter<TreatmentsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TreatmentsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_bolus_item, viewGroup, false)
            return TreatmentsViewHolder(v)
        }

        override fun onBindViewHolder(holder: TreatmentsViewHolder, position: Int) {
            val profile = profileFunction.getProfile() ?: return
            val t = treatments[position]
            holder.binding.date.text = dateUtil.dateAndTimeString(t.date)
            holder.binding.insulin.text = resourceHelper.gs(R.string.formatinsulinunits, t.insulin)
            holder.binding.carbs.text = resourceHelper.gs(R.string.format_carbs, t.carbs.toInt())
            val iob = t.iobCalc(System.currentTimeMillis(), profile.dia)
            holder.binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.iobContrib)
            holder.binding.mealOrCorrection.text = if (t.isSMB) "SMB" else if (t.mealBolus) resourceHelper.gs(R.string.mealbolus) else resourceHelper.gs(R.string.correctionbous)
            holder.binding.pump.visibility = if (t.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.binding.ns.visibility = if (NSUpload.isIdValid(t._id)) View.VISIBLE else View.GONE
            holder.binding.invalid.visibility = if (t.isValid) View.GONE else View.VISIBLE
            if (iob.iobContrib != 0.0) holder.binding.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.carbs.currentTextColor)
            if (t.date > DateUtil.now()) holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorScheduled)) else holder.binding.date.setTextColor(holder.binding.carbs.currentTextColor)
            holder.binding.remove.tag = t
            holder.binding.calculation.tag = t
            holder.binding.calculation.visibility = if (t.getBoluscalc() == null) View.INVISIBLE else View.VISIBLE
        }

        override fun getItemCount(): Int {
            return treatments.size
        }

        inner class TreatmentsViewHolder internal constructor(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsBolusItemBinding.bind(view)

            init {
                binding.calculation.setOnClickListener {
                    val treatment = it.tag as Treatment
                    if (treatment.getBoluscalc() != null) {
                        val wizardDialog = WizardInfoDialog()
                        wizardDialog.setData(treatment.getBoluscalc()!!)
                        wizardDialog.show(childFragmentManager, "WizardInfoDialog")
                    }
                }
                binding.calculation.paintFlags = binding.calculation.paintFlags or Paint.UNDERLINE_TEXT_FLAG
                binding.remove.setOnClickListener {
                    val treatment = it.tag as Treatment? ?: return@setOnClickListener
                    activity?.let { activity ->
                        val text = resourceHelper.gs(R.string.configbuilder_insulin) + ": " +
                            resourceHelper.gs(R.string.formatinsulinunits, treatment.insulin) + "\n" +
                            resourceHelper.gs(R.string.carbs) + ": " + resourceHelper.gs(R.string.format_carbs, treatment.carbs.toInt()) + "\n" +
                            resourceHelper.gs(R.string.date) + ": " + dateUtil.dateAndTimeString(treatment.date)
                        OKDialog.showConfirmation(activity, resourceHelper.gs(R.string.removerecord), text, Runnable {
                            uel.log("REMOVED TREATMENT", text)
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
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }
    }

    private fun updateGui() {
        if (_binding == null) return
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(treatmentsPlugin.treatmentsFromHistory), false)
        if (treatmentsPlugin.lastCalculationTreatments != null) {
            binding.iobTotal.text = resourceHelper.gs(R.string.formatinsulinunits, treatmentsPlugin.lastCalculationTreatments.iob)
            binding.iobActivityTotal.text = resourceHelper.gs(R.string.formatinsulinunits, treatmentsPlugin.lastCalculationTreatments.activity)
        }
        if (treatmentsPlugin.service.getTreatmentDataFromTime(DateUtil.now() + 1000, true).isNotEmpty())
            binding.deleteFutureTreatments.visibility = View.VISIBLE
        else
            binding.deleteFutureTreatments.visibility = View.GONE
    }
}