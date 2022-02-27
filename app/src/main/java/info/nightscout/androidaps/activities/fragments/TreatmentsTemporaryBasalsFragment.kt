package info.nightscout.androidaps.activities.fragments

import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.*
import androidx.appcompat.widget.Toolbar
import androidx.core.util.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.TemporaryBasal
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
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.activities.fragments.TreatmentsTemporaryBasalsFragment.RecyclerViewAdapter.TempBasalsViewHolder
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class TreatmentsTemporaryBasalsFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private var _binding: TreatmentsTempbasalsFragmentBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private val millsToThePast = T.days(30).msecs()
    private var selectedItems: SparseArray<TemporaryBasal> = SparseArray()
    private var showInvalidated = false
    private var toolbar: Toolbar? = null
    private var removeActionMode: ActionMode? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTempbasalsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = activity?.findViewById(R.id.toolbar)
        setHasOptionsMenu(true)
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
                if (showInvalidated)
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
                if (showInvalidated)
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
        removeActionMode?.let { it.finish() }
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
            val sameDayPrevious = position > 0 && dateUtil.isSameDay(tempBasal.timestamp, tempBasalList[position - 1].timestamp)
            holder.binding.date.visibility = sameDayPrevious.not().toVisibility()
            holder.binding.date.text = dateUtil.dateString(tempBasal.timestamp)
            if (tempBasal.isInProgress) {
                holder.binding.time.text = dateUtil.timeString(tempBasal.timestamp)
                holder.binding.time.setTextColor(rh.gc(R.color.colorActive))
            } else {
                holder.binding.time.text = dateUtil.timeRangeString(tempBasal.timestamp, tempBasal.end)
                holder.binding.time.setTextColor(holder.binding.duration.currentTextColor)
            }
            holder.binding.duration.text = rh.gs(R.string.format_mins, T.msecs(tempBasal.duration).mins())
            if (tempBasal.isAbsolute) holder.binding.rate.text = rh.gs(R.string.pump_basebasalrate, tempBasal.rate)
            else holder.binding.rate.text = rh.gs(R.string.format_percent, tempBasal.rate.toInt())
            val now = dateUtil.now()
            var iob = IobTotal(now)
            val profile = profileFunction.getProfile(now)
            if (profile != null) iob = tempBasal.iobCalc(now, profile, activePlugin.activeInsulin)
            holder.binding.iob.text = rh.gs(R.string.formatinsulinunits, iob.basaliob)
            holder.binding.extendedFlag.visibility = (tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED).toVisibility()
            holder.binding.suspendFlag.visibility = (tempBasal.type == TemporaryBasal.Type.PUMP_SUSPEND).toVisibility()
            holder.binding.emulatedSuspendFlag.visibility = (tempBasal.type == TemporaryBasal.Type.EMULATED_PUMP_SUSPEND).toVisibility()
            holder.binding.superBolusFlag.visibility = (tempBasal.type == TemporaryBasal.Type.SUPERBOLUS).toVisibility()
            if (abs(iob.basaliob) > 0.01) holder.binding.iob.setTextColor(rh.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.cbRemove.visibility = (tempBasal.isValid && removeActionMode != null).toVisibility()
            if (removeActionMode != null) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    if (value) {
                        selectedItems.put(position, tempBasal)
                    } else {
                        selectedItems.remove(position)
                    }
                    removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
                }
                holder.binding.cbRemove.isChecked = selectedItems.get(position) != null
            }
            val nextTimestamp = if (tempBasalList.size != position + 1) tempBasalList[position + 1].timestamp else 0L
            holder.binding.delimiter.visibility = dateUtil.isSameDay(tempBasal.timestamp, nextTimestamp).toVisibility()
        }

        override fun getItemCount() = tempBasalList.size

        inner class TempBasalsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsTempbasalsItemBinding.bind(itemView)

        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_treatments_temp_basal, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items -> {
                removeActionMode = toolbar?.startActionMode(RemoveActionModeCallback())
                true
            }

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            else -> false
        }

    inner class RemoveActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.menuInflater.inflate(R.menu.menu_delete_selection, menu)
            selectedItems.clear()
            mode.title = rh.gs(R.string.count_selected, selectedItems.size())
            binding.recyclerview.adapter?.notifyDataSetChanged()
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?) = false

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.remove_selected -> {
                    removeSelected()
                    true
                }

                else                 -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            removeActionMode = null
            binding.recyclerview.adapter?.notifyDataSetChanged()
        }
    }

    private fun getConfirmationText(): String {
        if (selectedItems.size() == 1) {
            val tempBasal = selectedItems.valueAt(0)
            val isFakeExtended = tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED
            val profile = profileFunction.getProfile(dateUtil.now())
            if (profile != null)
                return "${if (isFakeExtended) rh.gs(R.string.extended_bolus) else rh.gs(R.string.tempbasal_label)}: ${tempBasal.toStringFull(profile, dateUtil)}\n" +
                    "${rh.gs(R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.timestamp)}"
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected() {
        if (selectedItems.size() > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(), Runnable {
                    selectedItems.forEach {_, tempBasal ->
                        var extendedBolus: ExtendedBolus? = null
                        val isFakeExtended = tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED
                        if (isFakeExtended) {
                            val eb = repository.getExtendedBolusActiveAt(tempBasal.timestamp).blockingGet()
                            extendedBolus = if (eb is ValueWrapper.Existing) eb.value else null
                        }
                        if (isFakeExtended && extendedBolus != null) {
                            uel.log(
                                Action.EXTENDED_BOLUS_REMOVED, Sources.Treatments,
                                ValueWithUnit.Timestamp(extendedBolus.timestamp),
                                ValueWithUnit.Insulin(extendedBolus.amount),
                                ValueWithUnit.UnitPerHour(extendedBolus.rate),
                                ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(extendedBolus.duration).toInt())
                            )
                            disposable += repository.runTransactionForResult(InvalidateExtendedBolusTransaction(extendedBolus.id))
                                .subscribe(
                                    { aapsLogger.debug(LTag.DATABASE, "Removed extended bolus $extendedBolus") },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating extended bolus", it) })
                        } else if (!isFakeExtended) {
                            uel.log(
                                Action.TEMP_BASAL_REMOVED, Sources.Treatments,
                                ValueWithUnit.Timestamp(tempBasal.timestamp),
                                if (tempBasal.isAbsolute) ValueWithUnit.UnitPerHour(tempBasal.rate) else ValueWithUnit.Percent(tempBasal.rate.toInt()),
                                ValueWithUnit.Minute(T.msecs(tempBasal.duration).mins().toInt())
                            )
                            disposable += repository.runTransactionForResult(InvalidateTemporaryBasalTransaction(tempBasal.id))
                                .subscribe(
                                    { aapsLogger.debug(LTag.DATABASE, "Removed temporary basal $tempBasal") },
                                    { aapsLogger.error(LTag.DATABASE, "Error while invalidating temporary basal", it) })
                        }
                    }
                    removeActionMode?.finish()
                })
            }
        else
            removeActionMode?.finish()
    }
}
