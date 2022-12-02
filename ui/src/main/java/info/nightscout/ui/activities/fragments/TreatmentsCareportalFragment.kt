package info.nightscout.ui.activities.fragments

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
import info.nightscout.core.ui.dialogs.OKDialog
import info.nightscout.core.ui.toast.ToastUtils
import info.nightscout.core.utils.ActionModeHelper
import info.nightscout.core.utils.fabric.FabricPrivacy
import info.nightscout.database.entities.TherapyEvent
import info.nightscout.database.entities.UserEntry.Action
import info.nightscout.database.entities.UserEntry.Sources
import info.nightscout.database.entities.ValueWithUnit
import info.nightscout.database.impl.AppRepository
import info.nightscout.database.impl.transactions.InvalidateAAPSStartedTherapyEventTransaction
import info.nightscout.database.impl.transactions.InvalidateTherapyEventTransaction
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.Translator
import info.nightscout.interfaces.logging.UserEntryLogger
import info.nightscout.rx.AapsSchedulers
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventNSClientRestart
import info.nightscout.rx.events.EventTherapyEventChange
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import info.nightscout.shared.extensions.toVisibility
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.utils.DateUtil
import info.nightscout.shared.utils.T
import info.nightscout.ui.R
import info.nightscout.ui.activities.fragments.TreatmentsCareportalFragment.RecyclerViewAdapter.TherapyEventsViewHolder
import info.nightscout.ui.databinding.TreatmentsCareportalFragmentBinding
import info.nightscout.ui.databinding.TreatmentsCareportalItemBinding
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsCareportalFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var repository: AppRepository
    @Inject lateinit var uel: UserEntryLogger

    private var _binding: TreatmentsCareportalFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private val disposable = CompositeDisposable()
    private val millsToThePast = T.days(30).msecs()
    private lateinit var actionHelper: ActionModeHelper<TherapyEvent>
    private var showInvalidated = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsCareportalFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

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

    private fun refreshFromNightscout() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.careportal), rh.gs(R.string.refresheventsfromnightscout) + " ?", Runnable {
                uel.log(Action.CAREPORTAL_NS_REFRESH, Sources.Treatments)
                disposable += Completable.fromAction { repository.deleteAllTherapyEventsEntries() }
                    .subscribeOn(aapsSchedulers.io)
                    .subscribeBy(
                        onError = { aapsLogger.error("Error removing entries", it) },
                        onComplete = { rxBus.send(EventTherapyEventChange()) }
                    )
                rxBus.send(EventNSClientRestart())
            })
        }
    }

    private fun removeStartedEvents() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.careportal), rh.gs(R.string.careportal_remove_started_events), Runnable {
                uel.log(Action.RESTART_EVENTS_REMOVED, Sources.Treatments)
                disposable += repository.runTransactionForResult(InvalidateAAPSStartedTherapyEventTransaction(rh.gs(info.nightscout.core.ui.R.string.androidaps_start)))
                    .subscribe(
                        { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated therapy event $it") } },
                        { aapsLogger.error(LTag.DATABASE, "Error while invalidating therapy event", it) }
                    )
            })
        }
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (showInvalidated)
                repository
                    .getTherapyEventDataIncludingInvalidFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
            else
                repository
                    .getTherapyEventDataFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        swapAdapter()
        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.main)
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

    inner class RecyclerViewAdapter internal constructor(private var therapyList: List<TherapyEvent>) : RecyclerView.Adapter<TherapyEventsViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TherapyEventsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_careportal_item, viewGroup, false)
            return TherapyEventsViewHolder(v)
        }

        override fun onBindViewHolder(holder: TherapyEventsViewHolder, position: Int) {
            val therapyEvent = therapyList[position]
            holder.binding.ns.visibility = (therapyEvent.interfaceIDs.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = therapyEvent.isValid.not().toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDayGroup(therapyEvent.timestamp, therapyList[position - 1].timestamp)
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(therapyEvent.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeString(therapyEvent.timestamp)
            holder.binding.duration.text = if (therapyEvent.duration == 0L) "" else dateUtil.niceTimeScalar(therapyEvent.duration, rh)
            holder.binding.note.text = therapyEvent.note
            holder.binding.type.text = translator.translate(therapyEvent.type)
            holder.binding.cbRemove.visibility = (therapyEvent.isValid && actionHelper.isRemoving).toVisibility()
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, therapyEvent, value)
            }
            holder.binding.root.setOnClickListener {
                holder.binding.cbRemove.toggle()
                actionHelper.updateSelection(position, therapyEvent, holder.binding.cbRemove.isChecked)
            }
            holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
        }

        override fun getItemCount() = therapyList.size

        inner class TherapyEventsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsCareportalItemBinding.bind(view)
        }

    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_careportal, menu)
        updateMenuVisibility()
        val nsUploadOnly = !sp.getBoolean(info.nightscout.core.utils.R.string.key_ns_receive_therapy_events, false) || !config.isEngineeringMode()
        menu.findItem(R.id.nav_refresh_ns)?.isVisible = !nsUploadOnly
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu?.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items          -> actionHelper.startRemove()

            R.id.nav_show_invalidated      -> {
                showInvalidated = true
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                swapAdapter()
                true
            }

            R.id.nav_hide_invalidated      -> {
                showInvalidated = false
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.hide_invalidated_records)
                swapAdapter()
                true
            }

            R.id.nav_remove_started_events -> {
                removeStartedEvents()
                true
            }

            R.id.nav_refresh_ns            -> {
                refreshFromNightscout()
                true
            }

            else                           -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<TherapyEvent>): String {
        if (selectedItems.size() == 1) {
            val therapyEvent = selectedItems.valueAt(0)
            return rh.gs(info.nightscout.core.ui.R.string.event_type) + ": " + translator.translate(therapyEvent.type) + "\n" +
                rh.gs(info.nightscout.core.ui.R.string.notes_label) + ": " + (therapyEvent.note ?: "") + "\n" +
                rh.gs(info.nightscout.core.ui.R.string.date) + ": " + dateUtil.dateAndTimeString(therapyEvent.timestamp)
        }
        return rh.gs(info.nightscout.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<TherapyEvent>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(info.nightscout.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, therapyEvent ->
                    uel.log(
                        Action.CAREPORTAL_REMOVED, Sources.Treatments, therapyEvent.note,
                        ValueWithUnit.Timestamp(therapyEvent.timestamp),
                        ValueWithUnit.TherapyEventType(therapyEvent.type)
                    )
                    disposable += repository.runTransactionForResult(InvalidateTherapyEventTransaction(therapyEvent.id))
                        .subscribe(
                            { result -> result.invalidated.forEach { aapsLogger.debug(LTag.DATABASE, "Invalidated therapy event $it") } },
                            { aapsLogger.error(LTag.DATABASE, "Error while invalidating therapy event", it) }
                        )
                }
                actionHelper.finish()
            })
        }
    }
}
