package info.nightscout.androidaps.plugins.treatments.fragments

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
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Intervals
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTemporaryBasalsFragment.RecyclerViewAdapter.TempBasalsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog.showConfirmation
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.treatments_tempbasals_fragment.*
import javax.inject.Inject

class TreatmentsTemporaryBasalsFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.treatments_tempbasals_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        tempbasals_recyclerview.setHasFixedSize(true)
        tempbasals_recyclerview.layoutManager = LinearLayoutManager(view.context)
        tempbasals_recyclerview.adapter = RecyclerViewAdapter(activePlugin.activeTreatments.temporaryBasalsFromHistory)
    }

    @Synchronized override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
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

    inner class RecyclerViewAdapter internal constructor(private var tempBasalList: Intervals<TemporaryBasal>) : RecyclerView.Adapter<TempBasalsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempBasalsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_tempbasals_item, viewGroup, false)
            return TempBasalsViewHolder(v)
        }

        override fun onBindViewHolder(holder: TempBasalsViewHolder, position: Int) {
            val tempBasal = tempBasalList.getReversed(position)
            holder.ph.visibility = if (tempBasal.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.ns.visibility = if (NSUpload.isIdValid(tempBasal._id)) View.VISIBLE else View.GONE
            if (tempBasal.isEndingEvent) {
                holder.date.text = dateUtil.dateAndTimeString(tempBasal.date)
                holder.duration.text = resourceHelper.gs(R.string.cancel)
                holder.absolute.text = ""
                holder.percent.text = ""
                holder.realDuration.text = ""
                holder.iob.text = ""
                holder.netInsulin.text = ""
                holder.netRatio.text = ""
                holder.extendedFlag.visibility = View.GONE
                holder.iob.setTextColor(holder.netRatio.currentTextColor)
            } else {
                if (tempBasal.isInProgress) {
                    holder.date.text = dateUtil.dateAndTimeString(tempBasal.date)
                    holder.date.setTextColor(resourceHelper.gc(R.color.colorActive))
                } else {
                    holder.date.text = dateUtil.dateAndTimeRangeString(tempBasal.date, tempBasal.end())
                    holder.date.setTextColor(holder.netRatio.currentTextColor)
                }
                holder.duration.text = resourceHelper.gs(R.string.format_mins, tempBasal.durationInMinutes)
                if (tempBasal.isAbsolute) {
                    val profile = profileFunction.getProfile(tempBasal.date)
                    if (profile != null) {
                        holder.absolute.text = resourceHelper.gs(R.string.pump_basebasalrate, tempBasal.tempBasalConvertedToAbsolute(tempBasal.date, profile))
                        holder.percent.text = ""
                    } else {
                        holder.absolute.text = resourceHelper.gs(R.string.noprofile)
                        holder.percent.text = ""
                    }
                } else {
                    holder.absolute.text = ""
                    holder.percent.text = resourceHelper.gs(R.string.format_percent, tempBasal.percentRate)
                }
                holder.realDuration.text = resourceHelper.gs(R.string.format_mins, tempBasal.realDuration)
                val now = DateUtil.now()
                var iob = IobTotal(now)
                val profile = profileFunction.getProfile(now)
                if (profile != null) iob = tempBasal.iobCalc(now, profile)
                holder.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.basaliob)
                holder.netInsulin.text = resourceHelper.gs(R.string.formatinsulinunits, iob.netInsulin)
                holder.netRatio.text = resourceHelper.gs(R.string.pump_basebasalrate, iob.netRatio)
                holder.extendedFlag.visibility = View.GONE
                if (iob.basaliob != 0.0) holder.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.iob.setTextColor(holder.netRatio.currentTextColor)
            }
            holder.remove.tag = tempBasal
        }

        override fun getItemCount(): Int {
            return tempBasalList.size()
        }

        inner class TempBasalsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            var cv: CardView = itemView.findViewById(R.id.tempbasals_cardview)
            var date: TextView = itemView.findViewById(R.id.tempbasals_date)
            var duration: TextView = itemView.findViewById(R.id.tempbasals_duration)
            var absolute: TextView = itemView.findViewById(R.id.tempbasals_absolute)
            var percent: TextView = itemView.findViewById(R.id.tempbasals_percent)
            var realDuration: TextView = itemView.findViewById(R.id.tempbasals_realduration)
            var netRatio: TextView = itemView.findViewById(R.id.tempbasals_netratio)
            var netInsulin: TextView = itemView.findViewById(R.id.tempbasals_netinsulin)
            var iob: TextView = itemView.findViewById(R.id.tempbasals_iob)
            var extendedFlag: TextView = itemView.findViewById(R.id.tempbasals_extendedflag)
            var remove: TextView = itemView.findViewById(R.id.tempbasals_remove)
            var ph: TextView = itemView.findViewById(R.id.pump_sign)
            var ns: TextView = itemView.findViewById(R.id.ns_sign)

            init {
                remove.setOnClickListener { v: View ->
                    val tempBasal = v.tag as TemporaryBasal
                    context?.let {
                        showConfirmation(it, resourceHelper.gs(R.string.removerecord),
                            """
                ${resourceHelper.gs(R.string.tempbasal_label)}: ${tempBasal.toStringFull()}
                ${resourceHelper.gs(R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.date)}
                """.trimIndent(),
                            DialogInterface.OnClickListener { _: DialogInterface?, _: Int ->
                                activePlugin.activeTreatments.removeTempBasal(tempBasal)
                            }, null)
                    }
                }
                remove.paintFlags = remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }

    private fun updateGui() {
        tempbasals_recyclerview?.swapAdapter(RecyclerViewAdapter(activePlugin.activeTreatments.temporaryBasalsFromHistory), false)
        val tempBasalsCalculation = activePlugin.activeTreatments.lastCalculationTempBasals
        if (tempBasalsCalculation != null) tempbasals_totaltempiob?.text = resourceHelper.gs(R.string.formatinsulinunits, tempBasalsCalculation.basaliob)
    }
}