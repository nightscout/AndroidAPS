package info.nightscout.androidaps.activities.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.*
import androidx.core.util.forEach
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.android.support.DaggerFragment
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.fragments.TreatmentsTempTargetFragment.RecyclerViewAdapter.TempTargetsViewHolder
import info.nightscout.androidaps.database.AppRepository
import info.nightscout.androidaps.database.ValueWrapper
import info.nightscout.androidaps.database.entities.TemporaryTarget
import info.nightscout.androidaps.database.entities.UserEntry.Action
import info.nightscout.androidaps.database.entities.UserEntry.Sources
import info.nightscout.androidaps.database.entities.ValueWithUnit
import info.nightscout.androidaps.database.interfaces.end
import info.nightscout.androidaps.database.transactions.InvalidateTemporaryTargetTransaction
import info.nightscout.androidaps.databinding.TreatmentsTemptargetFragmentBinding
import info.nightscout.androidaps.databinding.TreatmentsTemptargetItemBinding
import info.nightscout.androidaps.events.EventEffectiveProfileSwitchChanged
import info.nightscout.androidaps.events.EventProfileSwitchChanged
import info.nightscout.androidaps.events.EventTempTargetChange
import info.nightscout.androidaps.events.EventTreatmentUpdateGui
import info.nightscout.androidaps.extensions.friendlyDescription
import info.nightscout.androidaps.extensions.highValueToUnitsToString
import info.nightscout.androidaps.extensions.lowValueToUnitsToString
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.logging.UserEntryLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.nsclient.events.EventNSClientRestart
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventNewHistoryData
import info.nightscout.androidaps.utils.*
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.buildHelper.BuildHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import io.reactivex.rxjava3.kotlin.subscribeBy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsTempTargetFragment : DaggerFragment() {

    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var buildHelper: BuildHelper
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
        actionHelper = ActionModeHelper(rh, activity)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        setHasOptionsMenu(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
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

    fun swapAdapter() {
        val now = System.currentTimeMillis()
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
        disposable += rxBus
            .toObservable(EventTreatmentUpdateGui::class.java) // TODO join with above
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
            holder.binding.duration.text = rh.gs(R.string.format_mins, T.msecs(tempTarget.duration).mins())
            holder.binding.low.text = tempTarget.lowValueToUnitsToString(units)
            holder.binding.high.text = tempTarget.highValueToUnitsToString(units)
            holder.binding.reason.text = translator.translate(tempTarget.reason)
            holder.binding.time.setTextColor(
                when {
                    tempTarget.id == currentlyActiveTarget?.id -> rh.gac(context , R.attr.activeColor)
                    tempTarget.timestamp > dateUtil.now()      -> rh.gac(context , R.attr.scheduledColor)
                    else                                       -> holder.binding.reasonColon.currentTextColor
                }
            )
            val nextTimestamp = if (tempTargetList.size != position + 1) tempTargetList[position + 1].timestamp else 0L
            holder.binding.delimiter.visibility = dateUtil.isSameDayGroup(tempTarget.timestamp, nextTimestamp).toVisibility()
        }

        override fun getItemCount() = tempTargetList.size

        inner class TempTargetsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsTemptargetItemBinding.bind(view)

        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_temp_target, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu?.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        updateMenuVisibility()
        val nsUploadOnly = !sp.getBoolean(R.string.key_ns_receive_temp_target, false) || !buildHelper.isEngineeringMode()
        menu.findItem(R.id.nav_refresh_ns)?.isVisible = !nsUploadOnly

        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items -> actionHelper.startRemove()

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                updateMenuVisibility()
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.show_invalidated_records))
                rxBus.send(EventTreatmentUpdateGui())
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                updateMenuVisibility()
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.show_invalidated_records))
                rxBus.send(EventTreatmentUpdateGui())
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
            return "${rh.gs(R.string.careportal_temporarytarget)}: ${tempTarget.friendlyDescription(profileFunction.getUnits(), rh)}\n" +
                dateUtil.dateAndTimeString(tempTarget.timestamp)
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<TemporaryTarget>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(R.string.removerecord), getConfirmationText(selectedItems), Runnable {
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
