package info.nightscout.androidaps.plugins.general.overview.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.*
import androidx.core.util.forEach
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.DaggerAppCompatActivityWithResult
import info.nightscout.androidaps.databinding.OverviewQuickwizardlistActivityBinding
import info.nightscout.androidaps.databinding.OverviewQuickwizardlistItemBinding
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.dragHelpers.ItemTouchHelperAdapter
import info.nightscout.androidaps.utils.dragHelpers.OnStartDragListener
import info.nightscout.androidaps.utils.dragHelpers.SimpleItemTouchHelperCallback
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange
import info.nightscout.androidaps.utils.ActionModeHelper
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.alertDialogs.OKDialog
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.wizard.QuickWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class QuickWizardListActivity : DaggerAppCompatActivityWithResult(), OnStartDragListener {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP

    private var disposable: CompositeDisposable = CompositeDisposable()
    private lateinit var actionHelper: ActionModeHelper<QuickWizardEntry>
    private val itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback())
    private lateinit var binding: OverviewQuickwizardlistActivityBinding

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    private inner class RecyclerViewAdapter(var fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerViewAdapter.QuickWizardEntryViewHolder>(), ItemTouchHelperAdapter {

        private inner class QuickWizardEntryViewHolder(val binding: OverviewQuickwizardlistItemBinding, val fragmentManager: FragmentManager) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickWizardEntryViewHolder {
            val binding = OverviewQuickwizardlistItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return QuickWizardEntryViewHolder(binding, fragmentManager)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: QuickWizardEntryViewHolder, position: Int) {
            val entry = quickWizard[position]
            holder.binding.from.text = dateUtil.timeString(entry.validFromDate())
            holder.binding.to.text = dateUtil.timeString(entry.validToDate())
            holder.binding.buttonText.text = entry.buttonText()
            holder.binding.carbs.text = rh.gs(R.string.format_carbs, entry.carbs())
            if (entry.device() == QuickWizardEntry.DEVICE_ALL) {
                holder.binding.device.visibility = View.GONE
            } else {
                holder.binding.device.visibility = View.VISIBLE
                holder.binding.device.setImageResource(
                    when (quickWizard[position].device()) {
                        QuickWizardEntry.DEVICE_WATCH -> R.drawable.ic_watch
                        else                          -> R.drawable.ic_smartphone
                    }
                )
            }
            holder.binding.root.setOnClickListener {
                if (actionHelper.isNoAction) {
                    val manager = fragmentManager
                    val editQuickWizardDialog = EditQuickWizardDialog()
                    val bundle = Bundle()
                    bundle.putInt("position", position)
                    editQuickWizardDialog.arguments = bundle
                    editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
                } else if (actionHelper.isRemoving) {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, entry, holder.binding.cbRemove.isChecked)
                }
            }
            holder.binding.sortHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    onStartDrag(holder)
                    return@setOnTouchListener true
                }
                return@setOnTouchListener false
            }
            holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, entry, value)
            }
            holder.binding.sortHandle.visibility = actionHelper.isSorting.toVisibility()
            holder.binding.cbRemove.visibility = actionHelper.isRemoving.toVisibility()
        }

        override fun getItemCount() = quickWizard.size()

        override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
            binding.recyclerview.adapter?.notifyItemMoved(fromPosition, toPosition)
            quickWizard.move(fromPosition, toPosition)
            return true
        }

        override fun onDrop() = rxBus.send(EventQuickWizardChange())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OverviewQuickwizardlistActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        actionHelper = ActionModeHelper(rh, this)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        actionHelper.enableSort = true

        title = rh.gs(R.string.quickwizard)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(supportFragmentManager)
        itemTouchHelper.attachToRecyclerView(binding.recyclerview)

        binding.addButton.setOnClickListener {
            actionHelper.finish()
            val manager = supportFragmentManager
            val editQuickWizardDialog = EditQuickWizardDialog()
            editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
        }
    }

    override fun onResume() {
        super.onResume()
        disposable += rxBus
            .toObservable(EventQuickWizardChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({
                           val adapter = RecyclerViewAdapter(supportFragmentManager)
                           binding.recyclerview.swapAdapter(adapter, false)
                       }, fabricPrivacy::logException)
    }

    override fun onPause() {
        disposable.clear()
        actionHelper.finish()
        super.onPause()
    }

    private fun removeSelected(selectedItems: SparseArray<QuickWizardEntry>) {
        OKDialog.showConfirmation(this, rh.gs(R.string.removerecord), getConfirmationText(selectedItems), Runnable {
            selectedItems.forEach { _, item ->
                quickWizard.remove(item.position)
                rxBus.send(EventQuickWizardChange())
            }
            actionHelper.finish()
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_actions, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }

            else              -> actionHelper.onOptionsItemSelected(item)

        }

    private fun getConfirmationText(selectedItems: SparseArray<QuickWizardEntry>): String {
        if (selectedItems.size() == 1) {
            val entry = selectedItems.valueAt(0)
            return "${rh.gs(R.string.remove_button)} ${entry.buttonText()} ${rh.gs(R.string.format_carbs, entry.carbs())}\n" +
                "${dateUtil.timeString(entry.validFromDate())} - ${dateUtil.timeString(entry.validToDate())}"
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }

}
