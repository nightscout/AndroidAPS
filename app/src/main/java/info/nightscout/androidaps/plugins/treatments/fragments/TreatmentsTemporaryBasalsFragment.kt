package info.nightscout.androidaps.plugins.treatments.fragments

import android.content.DialogInterface
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Intervals
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.databinding.TreatmentsTempbasalsFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsTempbasalsItemBinding
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.db.TemporaryBasal
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.NSUpload
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTemporaryBasalsFragment.RecyclerViewAdapter.TempBasalsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class TreatmentsTemporaryBasalsFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsTempbasalsFragmentBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTempbasalsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = RecyclerViewAdapter(activePlugin.activeTreatments.temporaryBasalsFromHistory)
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
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

    inner class RecyclerViewAdapter internal constructor(private var tempBasalList: Intervals<TemporaryBasal>) : RecyclerView.Adapter<TempBasalsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempBasalsViewHolder =
            TempBasalsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_tempbasals_item, viewGroup, false))

        override fun onBindViewHolder(holder: TempBasalsViewHolder, position: Int) {
            val tempBasal = tempBasalList.getReversed(position)
            holder.binding.ph.visibility = if (tempBasal.source == Source.PUMP) View.VISIBLE else View.GONE
            holder.binding.ns.visibility = if (NSUpload.isIdValid(tempBasal._id)) View.VISIBLE else View.GONE
            if (tempBasal.isEndingEvent) {
                holder.binding.date.text = dateUtil.dateAndTimeString(tempBasal.date)
                holder.binding.duration.text = resourceHelper.gs(R.string.cancel)
                holder.binding.absolute.text = ""
                holder.binding.percent.text = ""
                holder.binding.realDuration.text = ""
                holder.binding.iob.text = ""
                holder.binding.netInsulin.text = ""
                holder.binding.netRatio.text = ""
                holder.binding.extendedFlag.visibility = View.GONE
                holder.binding.iob.setTextColor(holder.binding.netRatio.currentTextColor)
            } else {
                if (tempBasal.isInProgress) {
                    holder.binding.date.text = dateUtil.dateAndTimeString(tempBasal.date)
                    holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorActive))
                } else {
                    holder.binding.date.text = dateUtil.dateAndTimeRangeString(tempBasal.date, tempBasal.end())
                    holder.binding.date.setTextColor(holder.binding.netRatio.currentTextColor)
                }
                holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, tempBasal.durationInMinutes)
                if (tempBasal.isAbsolute) {
                    val profile = profileFunction.getProfile(tempBasal.date)
                    if (profile != null) {
                        holder.binding.absolute.text = resourceHelper.gs(R.string.pump_basebasalrate, tempBasal.tempBasalConvertedToAbsolute(tempBasal.date, profile))
                        holder.binding.percent.text = ""
                    } else {
                        holder.binding.absolute.text = resourceHelper.gs(R.string.noprofile)
                        holder.binding.percent.text = ""
                    }
                } else {
                    holder.binding.absolute.text = ""
                    holder.binding.percent.text = resourceHelper.gs(R.string.format_percent, tempBasal.percentRate)
                }
                holder.binding.realDuration.text = resourceHelper.gs(R.string.format_mins, tempBasal.realDuration)
                val now = DateUtil.now()
                var iob = IobTotal(now)
                val profile = profileFunction.getProfile(now)
                if (profile != null) iob = tempBasal.iobCalc(now, profile)
                holder.binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.basaliob)
                holder.binding.netInsulin.text = resourceHelper.gs(R.string.formatinsulinunits, iob.netInsulin)
                holder.binding.netRatio.text = resourceHelper.gs(R.string.pump_basebasalrate, iob.netRatio)
                holder.binding.extendedFlag.visibility = View.GONE
                if (iob.basaliob != 0.0) holder.binding.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.netRatio.currentTextColor)
            }
            holder.binding.remove.tag = tempBasal
        }

        override fun getItemCount(): Int {
            return tempBasalList.size()
        }

        inner class TempBasalsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsTempbasalsItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val tempBasal = v.tag as TemporaryBasal
                    context?.let {
                        OKDialog.showConfirmation(it, resourceHelper.gs(R.string.removerecord),
                            """
                ${resourceHelper.gs(R.string.tempbasal_label)}: ${tempBasal.toStringFull()}
                ${resourceHelper.gs(R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.date)}
                """.trimIndent(),
                            { _: DialogInterface?, _: Int ->
                                uel.log("REMOVED TT", dateUtil.dateAndTimeString(tempBasal.date))
                                activePlugin.activeTreatments.removeTempBasal(tempBasal)
                            }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }

    private fun updateGui() {
        if (_binding == null) return
        binding.recyclerview.swapAdapter(RecyclerViewAdapter(activePlugin.activeTreatments.temporaryBasalsFromHistory), false)
        val tempBasalsCalculation = activePlugin.activeTreatments.lastCalculationTempBasals
        if (tempBasalsCalculation != null) binding.totalTempIob.text = resourceHelper.gs(R.string.formatinsulinunits, tempBasalsCalculation.basaliob)
    }
}