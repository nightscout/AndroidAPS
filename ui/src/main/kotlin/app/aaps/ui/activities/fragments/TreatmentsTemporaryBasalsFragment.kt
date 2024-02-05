package app.aaps.ui.activities.fragments

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
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.core.objects.extensions.toTemporaryBasal
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.activities.fragments.TreatmentsTemporaryBasalsFragment.RecyclerViewAdapter.TempBasalsViewHolder
import app.aaps.ui.databinding.TreatmentsTempbasalsFragmentBinding
import app.aaps.ui.databinding.TreatmentsTempbasalsItemBinding
import dagger.android.support.DaggerFragment
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
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var _binding: TreatmentsTempbasalsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<TB>
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

    private fun tempBasalsWithInvalid(now: Long) = persistenceLayer
        .getTemporaryBasalsStartingFromTimeIncludingInvalid(now - millsToThePast, false)

    private fun tempBasals(now: Long) = persistenceLayer
        .getTemporaryBasalsStartingFromTime(now - millsToThePast, false)

    private fun extendedBolusesWithInvalid(now: Long) = persistenceLayer
        .getExtendedBolusStartingFromTimeIncludingInvalid(now - millsToThePast, false)
        .map { eb -> eb.map { profileFunction.getProfile(it.timestamp)?.let { profile -> it.toTemporaryBasal(profile) } } }

    private fun extendedBoluses(now: Long) = persistenceLayer
        .getExtendedBolusesStartingFromTime(now - millsToThePast, false)
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

    inner class RecyclerViewAdapter internal constructor(private var tempBasalList: List<TB>) : RecyclerView.Adapter<TempBasalsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempBasalsViewHolder =
            TempBasalsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_tempbasals_item, viewGroup, false))

        override fun onBindViewHolder(holder: TempBasalsViewHolder, position: Int) {
            val tempBasal = tempBasalList[position]
            holder.binding.ns.visibility = (tempBasal.ids.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = tempBasal.isValid.not().toVisibility()
            holder.binding.ph.visibility = (tempBasal.ids.pumpId != null).toVisibility()
            val sameDayPrevious = position > 0 && dateUtil.isSameDay(tempBasal.timestamp, tempBasalList[position - 1].timestamp)
            holder.binding.date.visibility = sameDayPrevious.not().toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDayGroup(tempBasal.timestamp, tempBasalList[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(tempBasal.timestamp, rh) else ""
            if (tempBasal.isInProgress) {
                holder.binding.time.text = dateUtil.timeString(tempBasal.timestamp)
                holder.binding.time.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.activeColor))
            } else {
                holder.binding.time.text = dateUtil.timeRangeString(tempBasal.timestamp, tempBasal.end)
                holder.binding.time.setTextColor(holder.binding.duration.currentTextColor)
            }
            holder.binding.duration.text = rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(tempBasal.duration).mins())
            if (tempBasal.isAbsolute) holder.binding.rate.text = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, tempBasal.rate)
            else holder.binding.rate.text = rh.gs(app.aaps.core.ui.R.string.format_percent, tempBasal.rate.toInt())
            val now = dateUtil.now()
            var iob = IobTotal(now)
            val profile = profileFunction.getProfile(now)
            if (profile != null) iob = tempBasal.iobCalc(now, profile, activePlugin.activeInsulin)
            holder.binding.iob.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, iob.basaliob)
            holder.binding.extendedFlag.visibility = (tempBasal.type == TB.Type.FAKE_EXTENDED).toVisibility()
            holder.binding.suspendFlag.visibility = (tempBasal.type == TB.Type.PUMP_SUSPEND).toVisibility()
            holder.binding.emulatedSuspendFlag.visibility = (tempBasal.type == TB.Type.EMULATED_PUMP_SUSPEND).toVisibility()
            holder.binding.superBolusFlag.visibility = (tempBasal.type == TB.Type.SUPERBOLUS).toVisibility()
            if (abs(iob.basaliob) > 0.01) holder.binding.iob.setTextColor(
                rh.gac(
                    context,
                    app.aaps.core.ui.R.attr.activeColor
                )
            ) else holder.binding.iob.setTextColor(holder.binding.duration.currentTextColor)
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
            R.id.nav_remove_items     -> actionHelper.startRemove()

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

            else                      -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<TB>): String {
        if (selectedItems.size() == 1) {
            val tempBasal = selectedItems.valueAt(0)
            val isFakeExtended = tempBasal.type == TB.Type.FAKE_EXTENDED
            val profile = profileFunction.getProfile(dateUtil.now())
            if (profile != null)
                return "${if (isFakeExtended) rh.gs(app.aaps.core.ui.R.string.extended_bolus) else rh.gs(app.aaps.core.ui.R.string.tempbasal_label)}: ${
                    tempBasal.toStringFull(
                        profile,
                        dateUtil,
                        rh
                    )
                }\n" +
                    "${rh.gs(app.aaps.core.ui.R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.timestamp)}"
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<TB>) {
        if (selectedItems.size() > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                    selectedItems.forEach { _, tempBasal ->
                        var extendedBolus: EB? = null
                        val isFakeExtended = tempBasal.type == TB.Type.FAKE_EXTENDED
                        if (isFakeExtended) {
                            extendedBolus = persistenceLayer.getExtendedBolusActiveAt(tempBasal.timestamp)
                        }
                        if (isFakeExtended && extendedBolus != null) {
                            disposable += persistenceLayer.invalidateExtendedBolus(
                                id = extendedBolus.id,
                                action = Action.EXTENDED_BOLUS_REMOVED,
                                source = Sources.Treatments,
                                listValues = listOf(
                                    ValueWithUnit.Timestamp(extendedBolus.timestamp),
                                    ValueWithUnit.Insulin(extendedBolus.amount),
                                    ValueWithUnit.UnitPerHour(extendedBolus.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(extendedBolus.duration).toInt())
                                )
                            )
                                .subscribe()
                        } else if (!isFakeExtended) {
                            disposable += persistenceLayer.invalidateTemporaryBasal(
                                id = tempBasal.id,
                                action = Action.TEMP_BASAL_REMOVED,
                                source = Sources.Treatments,
                                listValues = listOf(
                                    ValueWithUnit.Timestamp(tempBasal.timestamp),
                                    if (tempBasal.isAbsolute) ValueWithUnit.UnitPerHour(tempBasal.rate) else ValueWithUnit.Percent(tempBasal.rate.toInt()),
                                    ValueWithUnit.Minute(T.msecs(tempBasal.duration).mins().toInt())
                                )
                            )
                                .subscribe()
                        }
                    }
                    actionHelper.finish()
                })
            }
    }
}
