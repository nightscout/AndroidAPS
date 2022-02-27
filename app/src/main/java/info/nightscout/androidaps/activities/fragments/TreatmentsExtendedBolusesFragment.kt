package info.nightscout.androidaps.activities.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.*
import android.view.ActionMode
import androidx.appcompat.widget.Toolbar
import androidx.core.util.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.entities.ExtendedBolus
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.InvalidateExtendedBolusTransaction
import info.nightscout.androidaps.databinding.TreatmentsExtendedbolusFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsExtendedbolusItemBinding
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.extensions.iobCalc
import info.nightscout.androidaps.extensions.isInProgress
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ActivePlugin
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.activities.fragments.TreatmentsExtendedBolusesFragment.RecyclerViewAdapter.ExtendedBolusesViewHolder
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.T
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import info.nightscout.shared.logging.LTag
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsExtendedBolusesFragment : DaggerFragment() {

    private val disposable = CompositeDisposable()

    private val millsToThePast = T.days(30).msecs()

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private var _binding: TreatmentsExtendedbolusFragmentBinding? = null
    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    private var selectedItems: SparseArray<ExtendedBolus> = SparseArray()
    private var showInvalidated = false
    private var removeActionMode: ActionMode? = null
    private var toolbar: Toolbar? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsExtendedbolusFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        setHasOptionsMenu(true)
        toolbar = activity?.findViewById(R.id.toolbar)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        disposable += if (showInvalidated)
            repository
                .getExtendedBolusDataIncludingInvalidFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
        else
            repository
                .getExtendedBolusDataFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()

        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
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

    private inner class RecyclerViewAdapter(private var extendedBolusList: List<ExtendedBolus>) : RecyclerView.Adapter<ExtendedBolusesViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ExtendedBolusesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_extendedbolus_item, viewGroup, false)
            return ExtendedBolusesViewHolder(v)
        }

        override fun onBindViewHolder(holder: ExtendedBolusesViewHolder, position: Int) {
            val extendedBolus = extendedBolusList[position]
            holder.binding.ns.visibility = (extendedBolus.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.ph.visibility = (extendedBolus.interfaceIDs.pumpId != null).toVisibility()
            holder.binding.invalid.visibility = extendedBolus.isValid.not().toVisibility()
            val sameDayPrevious = position > 0 && dateUtil.isSameDay(extendedBolus.timestamp, extendedBolusList[position - 1].timestamp)
            holder.binding.date.visibility = sameDayPrevious.not().toVisibility()
            holder.binding.date.text = dateUtil.dateString(extendedBolus.timestamp)
            @SuppressLint("SetTextI18n")
            if (extendedBolus.isInProgress(dateUtil)) {
                holder.binding.time.text = dateUtil.timeString(extendedBolus.timestamp)
                holder.binding.time.setTextColor(rh.gc(R.color.colorActive))
            } else {
                holder.binding.time.text = dateUtil.timeRangeString(extendedBolus.timestamp, extendedBolus.end)
                holder.binding.time.setTextColor(holder.binding.insulin.currentTextColor)
            }
            val profile = profileFunction.getProfile(extendedBolus.timestamp) ?: return
            holder.binding.duration.text = rh.gs(R.string.format_mins, T.msecs(extendedBolus.duration).mins())
            holder.binding.insulin.text = rh.gs(R.string.formatinsulinunits, extendedBolus.amount)
            val iob = extendedBolus.iobCalc(System.currentTimeMillis(), profile, activePlugin.activeInsulin)
            holder.binding.iob.text = rh.gs(R.string.formatinsulinunits, iob.iob)
            holder.binding.ratio.text = rh.gs(R.string.pump_basebasalrate, extendedBolus.rate)
            if (iob.iob != 0.0) holder.binding.iob.setTextColor(rh.gc(R.color.colorActive)) else holder.binding.iob.setTextColor(holder.binding.insulin.currentTextColor)
            holder.binding.cbRemove.visibility = (extendedBolus.isValid && removeActionMode != null).toVisibility()
            if (removeActionMode != null) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    if (value) {
                        selectedItems.put(position, extendedBolus)
                    } else {
                        selectedItems.remove(position)
                    }
                    removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
                }
                holder.binding.cbRemove.isChecked = selectedItems.get(position) != null
            }
            val nextTimestamp = if (extendedBolusList.size != position + 1) extendedBolusList[position + 1].timestamp else 0L
            holder.binding.delimiter.visibility = dateUtil.isSameDay(extendedBolus.timestamp, nextTimestamp).toVisibility()
        }

        override fun getItemCount() = extendedBolusList.size

        inner class ExtendedBolusesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsExtendedbolusItemBinding.bind(itemView)
        }

    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_treatments_extended_bolus, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        menu.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
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
            val bolus = selectedItems.valueAt(0)
            return rh.gs(R.string.extended_bolus) + "\n" +
                "${rh.gs(R.string.date)}: ${dateUtil.dateAndTimeString(bolus.timestamp)}"
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected() {
        if (selectedItems.size() > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(), Runnable {
                    selectedItems.forEach { _, extendedBolus ->
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
                    }
                    removeActionMode?.finish()
                })
            }
        else
            removeActionMode?.finish()
    }
}
