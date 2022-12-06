package info.nightscout.ui.activities.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.core.extensions.iobCalc
import info.nightscout.core.extensions.toStringFull
import info.nightscout.core.extensions.toTemporaryBasal
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.ActionModeHelper
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.ExtendedBolus
import info.nightscout.database.entities.TemporaryBasal
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.entities.interfaces.end
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InvalidateExtendedBolusTransaction
import info.nightscout.database.impl.transactions.InvalidateTemporaryBasalTransaction
import info.nightscout.interfaces.iob.IobTotal
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventTempBasalChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.ui.R
import info.nightscout.ui.activities.fragments.TreatmentsTemporaryBasalsFragment.RecyclerViewAdapter.TempBasalsViewHolder
import info.nightscout.ui.databinding.TreatmentsTempbasalsFragmentBinding
import info.nightscout.ui.databinding.TreatmentsTempbasalsItemBinding
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class TreatmentsTemporaryBasalsFragment : DaggerFragment(), MenuProvider {

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
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<TemporaryBasal>
    private val millsToThePast = T.days(30).msecs()
    private var showInvalidated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTempbasalsFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        actionHelper = ActionModeHelper(rh, activity, this)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.emptyView = binding.noRecordsText
        binding.recyclerview.loadingView = binding.progressBar
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
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

    private fun swapAdapter() {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
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
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        actionHelper.finish()
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
            val sameDayPrevious = position > 0 && dateUtil.isSameDay(tempBasal.timestamp, tempBasalList[position - 1].timestamp)
            holder.binding.date.visibility = sameDayPrevious.not().toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDayGroup(tempBasal.timestamp, tempBasalList[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(tempBasal.timestamp, rh) else ""
            if (tempBasal.isInProgress) {
                holder.binding.time.text = dateUtil.timeString(tempBasal.timestamp)
                holder.binding.time.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.activeColor))
            } else {
                holder.binding.time.text = dateUtil.timeRangeString(tempBasal.timestamp, tempBasal.end)
                holder.binding.time.setTextColor(holder.binding.duration.currentTextColor)
            }
            holder.binding.duration.text = rh.gs(info.nightscout.core.ui.R.string.format_mins, T.msecs(tempBasal.duration).mins())
            if (tempBasal.isAbsolute) holder.binding.rate.text = rh.gs(info.nightscout.core.ui.R.string.pump_base_basal_rate, tempBasal.rate)
            else holder.binding.rate.text = rh.gs(info.nightscout.core.ui.R.string.format_percent, tempBasal.rate.toInt())
            val now = dateUtil.now()
            var iob = IobTotal(now)
            val profile = profileFunction.getProfile(now)
            if (profile != null) iob = tempBasal.iobCalc(now, profile, activePlugin.activeInsulin)
            holder.binding.iob.text = rh.gs(info.nightscout.interfaces.R.string.format_insulin_units, iob.basaliob)
            holder.binding.extendedFlag.visibility = (tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED).toVisibility()
            holder.binding.suspendFlag.visibility = (tempBasal.type == TemporaryBasal.Type.PUMP_SUSPEND).toVisibility()
            holder.binding.emulatedSuspendFlag.visibility = (tempBasal.type == TemporaryBasal.Type.EMULATED_PUMP_SUSPEND).toVisibility()
            holder.binding.superBolusFlag.visibility = (tempBasal.type == TemporaryBasal.Type.SUPERBOLUS).toVisibility()
            if (abs(iob.basaliob) > 0.01) holder.binding.iob.setTextColor(rh.gac(context, info.nightscout.core.ui.R.attr.activeColor)) else holder.binding.iob.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.cbRemove.visibility = (tempBasal.isValid && actionHelper.isRemoving).toVisibility()
            if (actionHelper.isRemoving) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    actionHelper.updateSelection(position, tempBasal, value)
                }
                holder.binding.root.setOnClickListener {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, tempBasal, holder.binding.cbRemove.isChecked)
                }
                holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            }
        }

        override fun getItemCount() = tempBasalList.size

        inner class TempBasalsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsTempbasalsItemBinding.bind(itemView)

        }

    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_temp_basal, menu)
        updateMenuVisibility()
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu?.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items -> actionHelper.startRemove()

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                swapAdapter()
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.hide_invalidated_records)
                swapAdapter()
                true
            }

            else -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<TemporaryBasal>): String {
        if (selectedItems.size() == 1) {
            val tempBasal = selectedItems.valueAt(0)
            val isFakeExtended = tempBasal.type == TemporaryBasal.Type.FAKE_EXTENDED
            val profile = profileFunction.getProfile(dateUtil.now())
            if (profile != null)
                return "${if (isFakeExtended) rh.gs(info.nightscout.core.ui.R.string.extended_bolus) else rh.gs(info.nightscout.core.ui.R.string.tempbasal_label)}: ${tempBasal.toStringFull(profile, 
                                                                                                                                                                                            dateUtil)}\n" +
                    "${rh.gs(info.nightscout.core.ui.R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.timestamp)}"
        }
        return rh.gs(info.nightscout.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<TemporaryBasal>) {
        if (selectedItems.size() > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                    selectedItems.forEach { _, tempBasal ->
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
                    actionHelper.finish()
                })
            }
    }
}
