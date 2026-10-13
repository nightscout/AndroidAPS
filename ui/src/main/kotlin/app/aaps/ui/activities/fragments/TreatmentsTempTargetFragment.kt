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
import app.aaps.core.data.model.TT
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTempTargetChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.friendlyDescription
import app.aaps.core.objects.extensions.highValueToUnitsToString
import app.aaps.core.objects.extensions.lowValueToUnitsToString
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.activities.fragments.TreatmentsTempTargetFragment.RecyclerViewAdapter.TempTargetsViewHolder
import app.aaps.ui.databinding.TreatmentsTemptargetFragmentBinding
import app.aaps.ui.databinding.TreatmentsTemptargetItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsTempTargetFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var _binding: TreatmentsTemptargetFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<TT>
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

    private fun swapAdapter() {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (showInvalidated)
                persistenceLayer
                    .getTemporaryTargetDataIncludingInvalidFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list), true) }
            else
                persistenceLayer
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

    private inner class RecyclerViewAdapter(private var tempTargetList: List<TT>) : RecyclerView.Adapter<TempTargetsViewHolder>() {

        private val currentlyActiveTarget = persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempTargetsViewHolder =
            TempTargetsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_temptarget_item, viewGroup, false))

        @SuppressLint("SetTextI18n")
        override fun onBindViewHolder(holder: TempTargetsViewHolder, position: Int) {
            val units = profileUtil.units
            val tempTarget = tempTargetList[position]
            holder.binding.ns.visibility = (tempTarget.ids.nightscoutId != null).toVisibility()
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
            holder.binding.duration.text = rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(tempTarget.duration).mins())
            holder.binding.low.text = tempTarget.lowValueToUnitsToString(units, decimalFormatter)
            holder.binding.high.text = tempTarget.highValueToUnitsToString(units, decimalFormatter)
            holder.binding.reason.text = translator.translate(tempTarget.reason)
            holder.binding.time.setTextColor(
                when {
                    tempTarget.id == currentlyActiveTarget?.id -> rh.gac(context, app.aaps.core.ui.R.attr.activeColor)
                    tempTarget.timestamp > dateUtil.now()      -> rh.gac(context, app.aaps.core.ui.R.attr.scheduledColor)
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
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                swapAdapter()
                true
            }

            else                      -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<TT>): String {
        if (selectedItems.size() == 1) {
            val tempTarget = selectedItems.valueAt(0)
            return "${rh.gs(app.aaps.core.ui.R.string.temporary_target)}: ${tempTarget.friendlyDescription(profileUtil.units, rh, profileUtil)}\n" +
                dateUtil.dateAndTimeString(tempTarget.timestamp)
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

    private fun removeSelected(selectedItems: SparseArray<TT>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
                selectedItems.forEach { _, tempTarget ->
                    disposable += persistenceLayer.invalidateTemporaryTarget(
                        id = tempTarget.id,
                        action = Action.TT_REMOVED, source = Sources.Treatments, note = null,
                        listValues = listOf(
                            ValueWithUnit.Timestamp(tempTarget.timestamp),
                            ValueWithUnit.TETTReason(tempTarget.reason),
                            ValueWithUnit.Mgdl(tempTarget.lowTarget),
                            ValueWithUnit.Mgdl(tempTarget.highTarget).takeIf { tempTarget.lowTarget != tempTarget.highTarget },
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(tempTarget.duration).toInt())
                        ).filterNotNull()
                    ).subscribe()
                }
                actionHelper.finish()
            })
        }
    }
}
