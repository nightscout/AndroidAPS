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
import info.nightscout.core.extensions.friendlyDescription
import info.nightscout.core.extensions.highValueToUnitsToString
import info.nightscout.core.extensions.lowValueToUnitsToString
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.ActionModeHelper
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.ValueWrapper
import info.nightscout.database.entities.TemporaryTarget
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.entities.interfaces.end
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InvalidateTemporaryTargetTransaction
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Translator
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.interfaces.profile.ProfileFunction
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventEffectiveProfileSwitchChanged
import info.nightscout.rx.events.EventNSClientRestart
import info.nightscout.rx.events.EventNewHistoryData
import info.nightscout.rx.events.EventProfileSwitchChanged
import info.nightscout.rx.events.EventTempTargetChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.ui.R
import info.nightscout.ui.activities.fragments.TreatmentsTempTargetFragment.RecyclerViewAdapter.TempTargetsViewHolder
import info.nightscout.ui.databinding.TreatmentsTemptargetFragmentBinding
import info.nightscout.ui.databinding.TreatmentsTemptargetItemBinding
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsTempTargetFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var repository: AppRepository

    private var _binding: TreatmentsTemptargetFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<TemporaryTarget>
    private val disposable = CompositeDisposable()
    private val millsToThePast = T.days(30).msecs()
    private var showInvalidated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTemptargetFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    @SuppressLint("NotifyDataSetChanged")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerview.setHasFixedSize(true)
        actionHelper = ActionModeHelper(rh, activity, this)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.emptyView = binding.noRecordsText
        binding.recyclerview.loadingView = binding.progressBar
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    private fun refreshFromNightscout() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.refresheventsfromnightscout) + "?") {
                uel.log(Action.TREATMENTS_NS_REFRESH, Sources.Treatments)
                disposable +=
                    Completable.fromAction {
                        repository.deleteAllEffectiveProfileSwitches()
                        repository.deleteAllProfileSwitches()
                    }
                        .subscribeOn(aapsSchedulers.io)
                        .observeOn(aapsSchedulers.main)
                        .subscribeBy(
                            onError = { aapsLogger.error("Error removing entries", it) },
                            onComplete = {
                                rxBus.send(EventProfileSwitchChanged())
                                rxBus.send(EventEffectiveProfileSwitchChanged(0L))
                                rxBus.send(EventNewHistoryData(0, false))
                            }
                        )
                rxBus.send(EventNSClientRestart())
            }
        }
    }

    private fun swapAdapter() {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (showInvalidated)
                repository
                    .getTemporaryTargetDataIncludingInvalidFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
            else
                repository
                    .getTemporaryTargetDataFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()
        disposable += rxBus
            .toObservable(EventTempTargetChange::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
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

    private inner class RecyclerViewAdapter(private var tempTargetList: List<TemporaryTarget>) : RecyclerView.Adapter<TempTargetsViewHolder>() {

        private val dbRecord = repository.getTemporaryTargetActiveAt(dateUtil.now()).blockingGet()
        private val currentlyActiveTarget = if (dbRecord is ValueWrapper.Existing) dbRecord.value else null

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempTargetsViewHolder =
            TempTargetsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_temptarget_item, viewGroup, false))

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: TempTargetsViewHolder, position: Int) {
            val units = profileFunction.getUnits()
            val tempTarget = tempTargetList[position]
            holder.binding.ns.visibility = (tempTarget.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = tempTarget.isValid.not().toVisibility()
            holder.binding.cbRemove.visibility = (tempTarget.isValid && actionHelper.isRemoving).toVisibility()
            if (actionHelper.isRemoving) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    actionHelper.updateSelection(position, tempTarget, value)
                }
                holder.binding.root.setOnClickListener {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, tempTarget, holder.binding.cbRemove.isChecked)
                }
                holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            }
            val newDay = position == 0 || !dateUtil.isSameDayGroup(tempTarget.timestamp, tempTargetList[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(tempTarget.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeRangeString(tempTarget.timestamp, tempTarget.end)
            holder.binding.duration.text = rh.gs(info.nightscout.core.ui.R.string.format_mins, T.msecs(tempTarget.duration).mins())
            holder.binding.low.text = tempTarget.lowValueToUnitsToString(units)
            holder.binding.high.text = tempTarget.highValueToUnitsToString(units)
            holder.binding.reason.text = translator.translate(tempTarget.reason)
            holder.binding.time.setTextColor(
                when {
                    tempTarget.id == currentlyActiveTarget?.id -> rh.gac(context, info.nightscout.core.ui.R.attr.activeColor)
                    tempTarget.timestamp > dateUtil.now()      -> rh.gac(context, info.nightscout.core.ui.R.attr.scheduledColor)
                    else                                       -> holder.binding.reasonColon.currentTextColor
                }
            )
        }

        override fun getItemCount() = tempTargetList.size

        inner class TempTargetsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsTemptargetItemBinding.bind(view)

        }
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_temp_target, menu)
        updateMenuVisibility()
        val nsUploadOnly = !sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_temp_target, false) || !config.isEngineeringMode()
        menu.findItem(R.id.nav_refresh_ns)?.isVisible = !nsUploadOnly
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
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                swapAdapter()
                true
            }

            R.id.nav_refresh_ns -> {
                refreshFromNightscout()
                true
            }

            else -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<TemporaryTarget>): String {
        if (selectedItems.size() == 1) {
            val tempTarget = selectedItems.valueAt(0)
            return "${rh.gs(info.nightscout.core.ui.R.string.temporary_target)}: ${tempTarget.friendlyDescription(profileFunction.getUnits(), rh)}\n" +
                dateUtil.dateAndTimeString(tempTarget.timestamp)
        }
        return rh.gs(info.nightscout.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<TemporaryTarget>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, tempTarget ->
                    uel.log(
                        Action.TT_REMOVED, Sources.Treatments,
                        ValueWithUnit.Timestamp(tempTarget.timestamp),
                        ValueWithUnit.TherapyEventTTReason(tempTarget.reason),
                        ValueWithUnit.Mgdl(tempTarget.lowTarget),
                        ValueWithUnit.Mgdl(tempTarget.highTarget).takeIf { tempTarget.lowTarget != tempTarget.highTarget },
                        ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tempTarget.duration).toInt())
                    )
                    disposable += repository.runTransactionForResult(InvalidateTemporaryTargetTransaction(tempTarget.id))
                        .subscribe(
                            { aapsLogger.debug(LTag.DATABASE, "Removed temp target $tempTarget") },
                            { aapsLogger.error(LTag.DATABASE, "Error while invalidating temporary target", it) })
                }
                actionHelper.finish()
            })
        }
    }

}
