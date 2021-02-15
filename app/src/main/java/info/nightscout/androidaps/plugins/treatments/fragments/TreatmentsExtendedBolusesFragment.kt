package info.nightscout.androidaps.plugins.treatments.fragments

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Intervals
import info.nightscout.androidaps.databinding.TreatmentsExtendedbolusFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsExtendedbolusItemBinding
import info.nightscout.androidaps.db.ExtendedBolus
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsExtendedBolusesFragment.RecyclerViewAdapter.ExtendedBolusesViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class TreatmentsExtendedBolusesFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var nsUpload: NSUpload
    @Inject lateinit var uploadQueue: UploadQueue
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsExtendedbolusFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = TreatmentsExtendedbolusFragmentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = RecyclerViewAdapter(activePlugin.activeTreatments.extendedBolusesFromHistory)
    }

    private inner class RecyclerViewAdapter(private var extendedBolusList: Intervals<ExtendedBolus>) : RecyclerView.Adapter<ExtendedBolusesViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ExtendedBolusesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_extendedbolus_item, viewGroup, false)
            return ExtendedBolusesViewHolder(v)
        }

        override fun onBindViewHolder(holder: ExtendedBolusesViewHolder, position: Int) {
            val extendedBolus = extendedBolusList.getReversed(position)
            holder.binding.ph.visibility = if (extendedBolus.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.binding.ns.visibility = if (NSUpload.isIdValid(extendedBolus._id)) View.VISIBLE else View.GONE
            if (extendedBolus.isEndingEvent) {
                holder.binding.date.text = dateUtil.dateAndTimeString(extendedBolus.date)
                holder.binding.duration.text = resourceHelper.gs(R.string.cancel)
                holder.binding.insulin.text = ""
                holder.binding.realDuration.text = ""
                holder.binding.iob.text = ""
                holder.binding.insulinSoFar.text = ""
                holder.binding.ratio.text = ""
            } else {
                @SuppressLint("SetTextI18n")
                if (extendedBolus.isInProgress) holder.binding.date.text = dateUtil.dateAndTimeString(extendedBolus.date)
                else holder.binding.date.text = dateUtil.dateAndTimeString(extendedBolus.date) + " - " + dateUtil.timeString(extendedBolus.end())
                val profile = profileFunction.getProfile(extendedBolus.date)
                holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, extendedBolus.durationInMinutes)
                holder.binding.insulin.text = resourceHelper.gs(R.string.formatinsulinunits, extendedBolus.insulin)
                holder.binding.realDuration.text = resourceHelper.gs(R.string.format_mins, extendedBolus.realDuration)
                val iob = extendedBolus.iobCalc(System.currentTimeMillis(), profile)
                holder.binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.iob)
                holder.binding.insulinSoFar.text = resourceHelper.gs(R.string.formatinsulinunits, extendedBolus.insulinSoFar())
                holder.binding.ratio.text = resourceHelper.gs(R.string.pump_basebasalrate, extendedBolus.absoluteRate())
                if (extendedBolus.isInProgress) holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.date.setTextColor(holder.binding.insulin.currentTextColor)
                if (iob.iob != 0.0) holder.binding.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.insulin.currentTextColor)
            }
            holder.binding.remove.tag = extendedBolus
        }

        override fun getItemCount(): Int {
            return extendedBolusList.size()
        }

        inner class ExtendedBolusesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsExtendedbolusItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val extendedBolus = v.tag as ExtendedBolus
                    context?.let {
                        OKDialog.showConfirmation(it, resourceHelper.gs(R.string.removerecord),
                            """
                ${resourceHelper.gs(R.string.extended_bolus)}
                ${resourceHelper.gs(R.string.date)}: ${dateUtil.dateAndTimeString(extendedBolus.date)}
                """.trimIndent(), { _: DialogInterface, _: Int ->
                            uel.log("REMOVED EB")
                            val id = extendedBolus._id
                                            if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                                            else uploadQueue.removeID("dbAdd", id)
                                            MainApp.getDbHelper().delete(extendedBolus)
                                        }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
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

    @Synchronized override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateGui() {
        if (_binding == null) return
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(activePlugin.activeTreatments.extendedBolusesFromHistory), false)
    }
}