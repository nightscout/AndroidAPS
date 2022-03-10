package info.nightscout.androidaps.plugins.general.overview.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.*
import androidx.core.util.forEach
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.END
import androidx.recyclerview.widget.ItemTouchHelper.START
import androidx.recyclerview.widget.ItemTouchHelper.UP
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.DaggerAppCompatActivityWithResult
import info.nightscout.androidaps.databinding.OverviewQuickwizardlistActivityBinding
import info.nightscout.androidaps.databinding.OverviewQuickwizardlistItemBinding
import info.nightscout.androidaps.extensions.toVisibility
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange
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

class QuickWizardListActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP

    private var disposable: CompositeDisposable = CompositeDisposable()
    private var selectedItems: SparseArray<QuickWizardEntry> = SparseArray()
    private var removeActionMode: ActionMode? = null
    private var sortActionMode: ActionMode? = null

    private lateinit var binding: OverviewQuickwizardlistActivityBinding

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(UP or DOWN or START or END, 0) {

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val adapter = recyclerView.adapter as RecyclerViewAdapter
                val from = viewHolder.layoutPosition
                val to = target.layoutPosition
                adapter.moveItem(from, to)
                adapter.notifyItemMoved(from, to)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
                (recyclerView.adapter as RecyclerViewAdapter).onDrop()
            }
        }

        ItemTouchHelper(simpleItemTouchCallback)
    }

    fun startDragging(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    private inner class RecyclerViewAdapter(var fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerViewAdapter.QuickWizardEntryViewHolder>() {

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

            if (sortActionMode != null && removeActionMode != null) {
                holder.binding.cardview.setOnClickListener {
                    val manager = fragmentManager
                    val editQuickWizardDialog = EditQuickWizardDialog()
                    val bundle = Bundle()
                    bundle.putInt("position", position)
                    editQuickWizardDialog.arguments = bundle
                    editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
                }
            }

            fun updateSelection(selected: Boolean) {
                if (selected) {
                    selectedItems.put(position, entry)
                } else {
                    selectedItems.remove(position)
                }
                removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
            }

            holder.binding.cardview.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_UP && sortActionMode == null && removeActionMode == null) {
                    val manager = fragmentManager
                    val editQuickWizardDialog = EditQuickWizardDialog()
                    val bundle = Bundle()
                    bundle.putInt("position", position)
                    editQuickWizardDialog.arguments = bundle
                    editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN && sortActionMode != null) {
                    startDragging(holder)
                }
                if (event.actionMasked == MotionEvent.ACTION_UP && removeActionMode != null) {
                    holder.binding.cbRemove.toggle()
                    updateSelection(holder.binding.cbRemove.isChecked)
                }
                return@setOnTouchListener true
            }
            holder.binding.cbRemove.isChecked = selectedItems.get(position) != null
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value -> updateSelection(value) }
            holder.binding.handleView.visibility = (sortActionMode != null).toVisibility()
            holder.binding.cbRemove.visibility = (removeActionMode != null).toVisibility()
            removeActionMode?.title = rh.gs(R.string.count_selected, selectedItems.size())
        }

        override fun getItemCount(): Int = quickWizard.size()

        fun moveItem(from: Int, to: Int) {
            quickWizard.move(from, to)
        }

        fun onDrop() {
            rxBus.send(EventQuickWizardChange())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OverviewQuickwizardlistActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        title = rh.gs(R.string.quickwizard)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(this)
        binding.recyclerview.adapter = RecyclerViewAdapter(supportFragmentManager)
        itemTouchHelper.attachToRecyclerView(binding.recyclerview)

        binding.addButton.setOnClickListener {
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
        super.onPause()
    }

    private fun removeSelected() {
        if (selectedItems.size() > 0)
            OKDialog.showConfirmation(this, rh.gs(R.string.removerecord), getConfirmationText(), Runnable {
                selectedItems.forEach { _, item ->
                    quickWizard.remove(item.position)
                    rxBus.send(EventQuickWizardChange())
                }
                removeActionMode?.finish()
            })
        else
            removeActionMode?.finish()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_quickwizard, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            android.R.id.home     -> {
                finish()
                true
            }

            R.id.nav_remove_items -> {
                removeActionMode = startActionMode(RemoveActionModeCallback())
                true
            }

            R.id.nav_sort_items   -> {
                sortActionMode = startActionMode(SortActionModeCallback())
                true
            }

            else                  -> false
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

    inner class SortActionModeCallback : ActionMode.Callback {

        override fun onCreateActionMode(mode: ActionMode, menu: Menu?): Boolean {
            mode.title = rh.gs(R.string.sort_label)
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
            sortActionMode = null
            binding.recyclerview.adapter?.notifyDataSetChanged()
        }
    }

    private fun getConfirmationText(): String {
        if (selectedItems.size() == 1) {
            val entry = selectedItems.valueAt(0)
            return "${rh.gs(R.string.remove_button)} ${entry.buttonText()} ${rh.gs(R.string.format_carbs, entry.carbs())}\n" +
                "${dateUtil.timeString(entry.validFromDate())} - ${dateUtil.timeString(entry.validToDate())}"
        }
        return rh.gs(R.string.confirm_remove_multiple_items, selectedItems.size())
    }
}
