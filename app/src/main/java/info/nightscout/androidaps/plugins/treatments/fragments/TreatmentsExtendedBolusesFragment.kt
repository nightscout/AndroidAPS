package info.nightscout.androidaps.plugins.treatments.fragments

import android.annotation.SuppressLint
import android.content.DialogInterface
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
import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Intervals
import info.nightscout.androidaps.db.ExtendedBolus
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.general.nsclient.UploadQueue
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsExtendedBolusesFragment.RecyclerViewAdapter.ExtendedBolusesViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.treatments_extendedbolus_fragment.*
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_extendedbolus_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        extendedboluses_recyclerview.setHasFixedSize(true)
        extendedboluses_recyclerview.layoutManager = LinearLayoutManager(view.context)
        extendedboluses_recyclerview.adapter = RecyclerViewAdapter(activePlugin.activeTreatments.extendedBolusesFromHistory)
    }

    inner class RecyclerViewAdapter internal constructor(private var extendedBolusList: Intervals<ExtendedBolus>) : RecyclerView.Adapter<ExtendedBolusesViewHolder>() {
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ExtendedBolusesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_extendedbolus_item, viewGroup, false)
            return ExtendedBolusesViewHolder(v)
        }

        override fun onBindViewHolder(holder: ExtendedBolusesViewHolder, position: Int) {
            val extendedBolus = extendedBolusList.getReversed(position)
            holder.ph.visibility = if (extendedBolus.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.ns.visibility = if (NSUpload.isIdValid(extendedBolus._id)) View.VISIBLE else View.GONE
            if (extendedBolus.isEndingEvent) {
                holder.date.text = dateUtil.dateAndTimeString(extendedBolus.date)
                holder.duration.text = resourceHelper.gs(R.string.cancel)
                holder.insulin.text = ""
                holder.realDuration.text = ""
                holder.iob.text = ""
                holder.insulinSoFar.text = ""
                holder.ratio.text = ""
            } else {
                @SuppressLint("SetTextI18n")
                if (extendedBolus.isInProgress) holder.date.text = dateUtil.dateAndTimeString(extendedBolus.date)
                else holder.date.text = dateUtil.dateAndTimeString(extendedBolus.date) + " - " + dateUtil.timeString(extendedBolus.end())
                val profile = profileFunction.getProfile(extendedBolus.date)
                holder.duration.text = resourceHelper.gs(R.string.format_mins, extendedBolus.durationInMinutes)
                holder.insulin.text = resourceHelper.gs(R.string.formatinsulinunits, extendedBolus.insulin)
                holder.realDuration.text = resourceHelper.gs(R.string.format_mins, extendedBolus.realDuration)
                val iob = extendedBolus.iobCalc(System.currentTimeMillis(), profile)
                holder.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.iob)
                holder.insulinSoFar.text = resourceHelper.gs(R.string.formatinsulinunits, extendedBolus.insulinSoFar())
                holder.ratio.text = resourceHelper.gs(R.string.pump_basebasalrate, extendedBolus.absoluteRate())
                if (extendedBolus.isInProgress) holder.date.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.date.setTextColor(holder.insulin.currentTextColor)
                if (iob.iob != 0.0) holder.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.iob.setTextColor(holder.insulin.currentTextColor)
            }
            holder.remove.tag = extendedBolus
        }

        override fun getItemCount(): Int {
            return extendedBolusList.size()
        }

        inner class ExtendedBolusesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            var cv: CardView = itemView.findViewById(R.id.extendedboluses_cardview)
            var date: TextView = itemView.findViewById(R.id.extendedboluses_date)
            var duration: TextView = itemView.findViewById(R.id.extendedboluses_duration)
            var insulin: TextView = itemView.findViewById(R.id.extendedboluses_insulin)
            var realDuration: TextView = itemView.findViewById(R.id.extendedboluses_realduration)
            var ratio: TextView = itemView.findViewById(R.id.extendedboluses_ratio)
            var insulinSoFar: TextView = itemView.findViewById(R.id.extendedboluses_netinsulin)
            var iob: TextView = itemView.findViewById(R.id.extendedboluses_iob)
            var remove: TextView = itemView.findViewById(R.id.extendedboluses_remove)
            var ph: TextView = itemView.findViewById(R.id.pump_sign)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)

            init {
                remove.setOnClickListener { v: View ->
                    val extendedBolus = v.tag as ExtendedBolus
                    context?.let {
                        showConfirmation(it, resourceHelper.gs(R.string.removerecord),
                            """
                ${resourceHelper.gs(R.string.extended_bolus)}
                ${resourceHelper.gs(R.string.date)}: ${dateUtil.dateAndTimeString(extendedBolus.date)}
                """.trimIndent(), DialogInterface.OnClickListener { _: DialogInterface, _: Int ->
                            val id = extendedBolus._id
                            if (NSUpload.isIdValid(id)) nsUpload.removeCareportalEntryFromNS(id)
                            else uploadQueue.removeID("dbAdd", id)
                            MainApp.getDbHelper().delete(extendedBolus)
                        }, null)
                    }
                }
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
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

    private fun updateGui() =
        extendedboluses_recyclerview.swapAdapter(RecyclerViewAdapter(activePlugin.activeTreatments.extendedBolusesFromHistory), false)
}