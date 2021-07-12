package info.nightscout.androidaps.activities.fragments

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
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.TemporaryBasal
import info.nightscout.androidaps.database.entities.UserEntry.*
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.InvalidateExtendedBolusTransaction
import info.nightscout.androidaps.database.transactions.InvalidateTemporaryBasalTransaction
import info.nightscout.androidaps.databinding.TreatmentsTempbasalsFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsTempbasalsItemBinding
import info.nightscout.androidaps.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.extensions.iobCalc
import info.nightscout.androidaps.extensions.toStringFull
import info.nightscout.androidaps.extensions.toTemporaryBasal
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.activities.fragments.TreatmentsTemporaryBasalsFragment.RecyclerViewAdapter.TempBasalsViewHolder
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class TreatmentsTemporaryBasalsFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBusWrapper
    @Inject lateinit var resourceHelper: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
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

    private fun tempBasalsWithInvalid(now: Long) = repository
        .getTemporaryBasalsDataIncludingInvalidFromTime(now - millsToThePast, false)

    private fun tempBasals(now: Long) = repository
        .getTemporaryBasalsDataFromTime(now - millsToThePast, false)

    private fun extendedBolusesWithInvalid(now: Long) = repository
        .getExtendedBolusDataIncludingInvalidFromTime(now - millsToThePast, false)
        .map { eb -> eb.map { profileFunction.getProfile(it.timestamp)?.let { profile -> it.toTemporaryBasal(profile) } } }

    private fun extendedBoluses(now: Long) = repository
        .getExtendedBolusDataFromTime(now - millsToThePast, false)
        .map { eb -> eb.map { profileFunction.getProfile(it.timestamp)?.let { profile -> it.toTemporaryBasal(profile) } } }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        disposable +=
            if (activePlugin.activePump.isFakingTempsByExtendedBoluses) {
                if (binding.showInvalidated.isChecked)
                    tempBasalsWithInvalid(now)
                        .zipWith(extendedBolusesWithInvalid(now)) { first, second -> first + second }
                        .map { list -> list.filterNotNull() }
                        .map { list -> list.sortedByDescending { it.timestamp } }
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
                else
                    tempBasals(now)
                        .zipWith(extendedBoluses(now)) { first, second -> first + second }
                        .map { list -> list.filterNotNull() }
                        .map { list -> list.sortedByDescending { it.timestamp } }
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
            } else {
                if (binding.showInvalidated.isChecked)
                    tempBasalsWithInvalid(now)
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
                else
                    tempBasals(now)
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
            }

    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()

        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)

        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
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
            val now = dateUtil.now()
            var iob = IobTotal(now)
            val profile = profileFunction.getProfile(now)
            if (profile != null) iob = tempBasal.iobCalc(now, profile, activePlugin.activeInsulin)
            holder.binding.iob.text = resourceHelper.gs(R.string.formatinsulinunits, iob.basaliob)
            holder.binding.extendedFlag.visibility = (tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED).toVisibility()
            holder.binding.suspendFlag.visibility = (tempBasal.type == TemporaryBasal.Type.PUMP_SUSPEND).toVisibility()
            holder.binding.emulatedSuspendFlag.visibility = (tempBasal.type == TemporaryBasal.Type.EMULATED_PUMP_SUSPEND).toVisibility()
            holder.binding.superBolusFlag.visibility = (tempBasal.type == TemporaryBasal.Type.SUPERBOLUS).toVisibility()
            if (abs(iob.basaliob) > 0.01) holder.binding.iob.setTextColor(resourceHelper.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.remove.tag = tempBasal
        }

        override fun getItemCount(): Int = tempBasalList.size

        @Deprecated("remove remove functionality after finish")
        inner class TempBasalsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsTempbasalsItemBinding.bind(itemView)

            init {
                binding.remove.setOnClickListener { v: View ->
                    val tempBasal = v.tag as TemporaryBasal
                    var extendedBolus: ExtendedBolus? = null
                    val isFakeExtended = tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED
                    if (isFakeExtended) {
                        val eb = repository.getExtendedBolusActiveAt(tempBasal.timestamp).blockingGet()
                        extendedBolus = if (eb is ValueWrapper.Existing) eb.value else null
                    }
                    val profile = profileFunction.getProfile(dateUtil.now())
                        ?: return@setOnClickListener
                    context?.let {
                        OKDialog.showConfirmation(it, resourceHelper.gs(R.string.removerecord),
                            """
                ${if (isFakeExtended) resourceHelper.gs(R.string.extended_bolus) else resourceHelper.gs(R.string.tempbasal_label)}: ${tempBasal.toStringFull(profile, dateUtil)}
                ${resourceHelper.gs(R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.timestamp)}
                """.trimIndent(),
                            { _: DialogInterface?, _: Int ->
                                if (isFakeExtended && extendedBolus != null) {
                                    uel.log(Action.EXTENDED_BOLUS_REMOVED, Sources.Treatments,
                                        ValueWithUnit.Timestamp(extendedBolus.timestamp),
                                        ValueWithUnit.Insulin(extendedBolus.amount),
                                        ValueWithUnit.UnitPerHour(extendedBolus.rate),
                                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(extendedBolus.duration).toInt()))
                                    disposable += repository.runTransactionForResult(InvalidateExtendedBolusTransaction(extendedBolus.id))
                                        .subscribe(
                                            { aapsLogger.debug(LTag.DATABASE, "Removed extended bolus $extendedBolus") },
                                            { aapsLogger.error(LTag.DATABASE, "Error while invalidating extended bolus", it) })
                                } else if (!isFakeExtended) {
                                    uel.log(Action.TEMP_BASAL_REMOVED, Sources.Treatments,
                                        ValueWithUnit.Timestamp(tempBasal.timestamp),
                                        if (tempBasal.isAbsolute) ValueWithUnit.UnitPerHour(tempBasal.rate) else ValueWithUnit.Percent(tempBasal.rate.toInt()),
                                        ValueWithUnit.Minute(T.msecs(tempBasal.duration).mins().toInt()))
                                    disposable += repository.runTransactionForResult(InvalidateTemporaryBasalTransaction(tempBasal.id))
                                        .subscribe(
                                            { aapsLogger.debug(LTag.DATABASE, "Removed temporary basal $tempBasal") },
                                            { aapsLogger.error(LTag.DATABASE, "Error while invalidating temporary basal", it) })
                                }
                            }, null)
                    }
                }
                binding.remove.paintFlags = binding.remove.paintFlags or Paint.UNDERLINE_TEXT_FLAG
            }
        }

    }
}