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
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.UserEntry.*
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.InvalidateTemporaryBasalTransaction
import info.nightscout.androidaps.databinding.TreatmentsTempbasalsFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsTempbasalsItemBinding
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.treatments.fragments.TreatmentsTemporaryBasalsFragment.RecyclerViewAdapter.TempBasalsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.extensions.getPassedDurationToTimeInMinutes
import info.nightscout.androidaps.extensions.iobCalc
import info.nightscout.androidaps.extensions.toStringFull
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import javax.inject.Inject
import kotlin.math.abs

class TreatmentsTemporaryBasalsFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePluginProvider
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private var _binding: TreatmentsTempbasalsFragmentBinding? = null

    private val millsToThePast = T.days(30).msecs()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTempbasalsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        if (binding.showInvalidated.isChecked)
            repository
                .getTemporaryBasalsDataIncludingInvalidFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
        else
            repository
                .getTemporaryBasalsDataFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()

        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        )
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
        )
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null // avoid leaks
        _binding = null
    }

    inner class RecyclerViewAdapter internal constructor(private var tempBasalList: List<TemporaryBasal>) : RecyclerView.Adapter<TempBasalsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempBasalsViewHolder =
            TempBasalsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_tempbasals_item, viewGroup, false))

        override fun onBindViewHolder(holder: TempBasalsViewHolder, position: Int) {
            val tempBasal = tempBasalList[position]
            holder.binding.ns.visibility = (tempBasal.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = tempBasal.isValid.not().toVisibility()
            holder.binding.ph.visibility = (tempBasal.interfaceIDs.pumpId != null).toVisibility()
            if (tempBasal.isInProgress) {
                holder.binding.date.text = dateUtil.dateAndTimeString(tempBasal.timestamp)
                holder.binding.date.setTextColor(resourceHelper.gc(R.color.colorActive))
            } else {
                holder.binding.date.text = dateUtil.dateAndTimeRangeString(tempBasal.timestamp, tempBasal.end)
                holder.binding.date.setTextColor(holder.binding.duration.currentTextColor)
            }
            holder.binding.duration.text = resourceHelper.gs(R.string.format_mins, T.msecs(tempBasal.duration).mins())
            if (tempBasal.isAbsolute) holder.binding.rate.text = resourceHelper.gs(R.string.pump_basebasalrate, tempBasal.rate)
            else holder.binding.rate.text = resourceHelper.gs(R.string.format_percent, tempBasal.rate.toInt())
            val now = DateUtil.now()
            var iob = IobTotal(now)
            val profile = profileFunction.getProfile(now)
            if (profile != null) iob = tempBasal.iobCalc(now, profile, activePlugin.activeInsulin)
            holder.binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.basaliob)
            holder.binding.extendedFlag.visibility = (tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED).toVisibility()
            if (abs(iob.basaliob) > 0.01) holder.binding.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.type.text = tempBasal.type.toString()
            holder.binding.remove.tag = tempBasal
        }

        override fun getItemCount(): Int = tempBasalList.size

        @Deprecated("remove remove functionality after finish")
        inner class TempBasalsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsTempbasalsItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val tempBasal = v.tag as TemporaryBasal
                    val profile = profileFunction.getProfile(dateUtil._now())
                        ?: return@setOnClickListener
                    context?.let {
                        OKDialog.showConfirmation(it, resourceHelper.gs(R.string.removerecord),
                            """
                ${resourceHelper.gs(R.string.tempbasal_label)}: ${tempBasal.toStringFull(profile, dateUtil)}
                ${resourceHelper.gs(R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.timestamp)}
                """.trimIndent(),
                            { _: DialogInterface?, _: Int ->
                                uel.log(Action.TB_REMOVED, ValueWithUnit(tempBasal.timestamp, Units.Timestamp))
                                disposable += repository.runTransactionForResult(InvalidateTemporaryBasalTransaction(tempBasal.id))
                                    .subscribe(
                                        { aapsLogger.debug(LTag.DATABASE, "Removed temporary basal $tempBasal") },
                                        { aapsLogger.error(LTag.DATABASE, "Error while invalidating temporary basal", it) })
                            }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }
}