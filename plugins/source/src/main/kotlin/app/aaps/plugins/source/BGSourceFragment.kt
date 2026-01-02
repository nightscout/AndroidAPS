package app.aaps.plugins.source

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
import androidx.core.util.size
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.GV
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNewBG
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.directionToIcon
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.extensions.toVisibilityKeepSpace
import app.aaps.plugins.source.databinding.SourceFragmentBinding
import app.aaps.plugins.source.databinding.SourceItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class BGSourceFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var profileUtil: ProfileUtil

    private val disposable = CompositeDisposable()
    private var millsToThePast = T.hours(36).msecs()
    private lateinit var actionHelper: ActionModeHelper<GV>
    private var _binding: SourceFragmentBinding? = null
    private var adapter: BgListAdapter? = null

    class GVWithLabel(
        /** Original GV value */
        val gv: GV,
        /** true if displayed with date label */
        var hasLabel: Boolean = false
    )

    fun GV.withLabel() = GVWithLabel(this, false)

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        SourceFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            actionHelper = ActionModeHelper(rh, activity, this)
            actionHelper.setUpdateListHandler { adapter?.let { adapter -> for (i in 0 until adapter.currentList.size) adapter.notifyItemChanged(i) } }
            actionHelper.setOnRemoveHandler { handler -> removeSelected(handler) }
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = BgListAdapter()
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.adapter = adapter

        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Load more data if scrolled to the bottom
                if ((binding.recyclerview.layoutManager as LinearLayoutManager?)?.findLastCompletelyVisibleItemPosition() == (adapter?.currentList?.size ?: -1000) - 1) {
                    millsToThePast += T.hours(24).msecs()
                    load(withScroll = false)
                }
            }
        })

    }

    private fun load(withScroll: Boolean) {
        val now = System.currentTimeMillis()
        disposable += persistenceLayer
            .getBgReadingsDataFromTime(now - millsToThePast, false)
            .observeOn(aapsSchedulers.main)
            .subscribe { list -> adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) } }
    }

    override fun onResume() {
        super.onResume()
        load(withScroll = false)
        disposable += rxBus
            .toObservable(EventNewBG::class.java)
            .observeOn(aapsSchedulers.io)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ load(withScroll = true) }, fabricPrivacy::logException)
    }

    override fun onPause() {
        actionHelper.finish()
        disposable.clear()
        super.onPause()
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        actionHelper.onCreateOptionsMenu(menu, inflater)
        actionHelper.onPrepareOptionsMenu(menu)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null
        adapter = null
        _binding = null
    }

    override fun onMenuItemSelected(item: MenuItem) =
        if (actionHelper.onOptionsItemSelected(item)) true
        else super.onContextItemSelected(item)

    inner class BgListAdapter : ListAdapter<GVWithLabel, BgListAdapter.GlucoseValuesViewHolder>(GlucoseValueDiffCallback()) {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): GlucoseValuesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.source_item, viewGroup, false)
            return GlucoseValuesViewHolder(v)
        }

        override fun onBindViewHolder(holder: GlucoseValuesViewHolder, position: Int) {
            val item = getItem(position)
            val glucoseValue = item.gv
            holder.binding.ns.visibility = (glucoseValue.ids.nightscoutId != null).toVisibilityKeepSpace()
            holder.binding.invalid.visibility = (!glucoseValue.isValid).toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDay(glucoseValue.timestamp, getItem(position - 1).gv.timestamp)
            item.hasLabel = newDay
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(glucoseValue.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeStringWithSeconds(glucoseValue.timestamp)
            holder.binding.value.text = profileUtil.fromMgdlToStringInUnits(glucoseValue.value)
            holder.binding.direction.setImageResource(glucoseValue.trendArrow.directionToIcon())
            if (position > 0) {
                val previous = getItem(position - 1).gv
                val diff = previous.timestamp - glucoseValue.timestamp
                if (diff < T.secs(20).msecs())
                    holder.binding.root.setBackgroundColor(rh.gac(context, app.aaps.core.ui.R.attr.bgsourceError))
            }

            holder.binding.root.setOnLongClickListener {
                if (actionHelper.startRemove()) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, glucoseValue, holder.binding.cbRemove.isChecked)
                    return@setOnLongClickListener true
                }
                false
            }
            holder.binding.root.setOnClickListener {
                if (actionHelper.isRemoving) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, glucoseValue, holder.binding.cbRemove.isChecked)
                }
            }
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, glucoseValue, value)
            }
            holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            holder.binding.cbRemove.visibility = actionHelper.isRemoving.toVisibility()
        }

        inner class GlucoseValuesViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = SourceItemBinding.bind(view)
        }
    }

    private class GlucoseValueDiffCallback : DiffUtil.ItemCallback<GVWithLabel>() {

        override fun areItemsTheSame(oldItem: GVWithLabel, newItem: GVWithLabel): Boolean = oldItem.gv.id == newItem.gv.id

        override fun areContentsTheSame(oldItem: GVWithLabel, newItem: GVWithLabel): Boolean =
            oldItem.gv.timestamp == newItem.gv.timestamp &&
                oldItem.gv.value == newItem.gv.value &&
                oldItem.gv.trendArrow == newItem.gv.trendArrow &&
                oldItem.gv.isValid == newItem.gv.isValid &&
                oldItem.hasLabel == newItem.hasLabel
    }

    private fun getConfirmationText(selectedItems: SparseArray<GV>): String {
        if (selectedItems.size == 1) {
            val glucoseValue = selectedItems.valueAt(0)
            return dateUtil.dateAndTimeString(glucoseValue.timestamp) + "\n" + profileUtil.fromMgdlToUnits(glucoseValue.value)
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size)
    }

    @SuppressLint("CheckResult")
    private fun removeSelected(selectedItems: SparseArray<GV>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), {
                selectedItems.forEach { _, glucoseValue ->
                    disposable += persistenceLayer.invalidateGlucoseValue(
                        glucoseValue.id, action = Action.BG_REMOVED,
                        source = Sources.BgFragment, note = null,
                        listValues = listOf(ValueWithUnit.Timestamp(glucoseValue.timestamp))
                    ).subscribe()
                }
                actionHelper.finish()
            })
        }
    }
}
