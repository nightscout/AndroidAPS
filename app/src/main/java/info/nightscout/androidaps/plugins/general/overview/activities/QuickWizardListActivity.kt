package info.nightscout.androidaps.plugins.general.overview.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.ItemTouchHelper.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import info.nightscout.androidaps.R
import info.nightscout.androidaps.activities.NoSplashAppCompatActivity
import info.nightscout.androidaps.databinding.OverviewQuickwizardlistActivityBinding
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.overview.dialogs.EditQuickWizardDialog
import info.nightscout.androidaps.plugins.general.overview.events.EventQuickWizardChange
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import io.reactivex.rxkotlin.plusAssign
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.androidaps.utils.wizard.QuickWizard
import info.nightscout.androidaps.utils.wizard.QuickWizardEntry
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

class QuickWizardListActivity : NoSplashAppCompatActivity() {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sp: SP

    private var disposable: CompositeDisposable = CompositeDisposable()

    private lateinit var binding: OverviewQuickwizardlistActivityBinding

    private val itemTouchHelper by lazy {
        val simpleItemTouchCallback = object : ItemTouchHelper.SimpleCallback(UP or DOWN or START or END, 0) {

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val adapter = recyclerView.adapter as RecyclerViewAdapter
                val from = viewHolder.layoutPosition
                val to = target.layoutPosition
                adapter.moveItem(from, to)
                adapter.notifyItemMoved(from, to)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            }

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)

                if (actionState == ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.5f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)

                viewHolder.itemView.alpha = 1.0f

                val adapter = recyclerView.adapter as RecyclerViewAdapter
                adapter.onDrop()
            }
        }

        ItemTouchHelper(simpleItemTouchCallback)
    }

    fun startDragging(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    private inner class RecyclerViewAdapter(var fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerViewAdapter.QuickWizardEntryViewHolder>() {

        @SuppressLint("ClickableViewAccessibility")
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickWizardEntryViewHolder {
            val itemView = LayoutInflater.from(parent.context).inflate(R.layout.overview_quickwizardlist_item, parent, false)
            val viewHolder = QuickWizardEntryViewHolder(itemView, fragmentManager)

            viewHolder.handleView.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    startDragging(viewHolder)
                }
                return@setOnTouchListener true
            }

            return viewHolder
        }

        override fun onBindViewHolder(holder: QuickWizardEntryViewHolder, position: Int) {
            holder.from.text = dateUtil.timeString(quickWizard[position].validFromDate())
            holder.to.text = dateUtil.timeString(quickWizard[position].validToDate())
            val wearControl = sp.getBoolean(R.string.key_wear_control, false)

            if (wearControl) {
                holder.handleView.visibility = View.VISIBLE
            } else {
                holder.handleView.visibility = View.GONE
            }
            if (quickWizard[position].device() == QuickWizardEntry.DEVICE_ALL) {
                holder.device.visibility = View.GONE
            } else {
                holder.device.visibility = View.VISIBLE
                holder.device.setImageResource(
                    when (quickWizard[position].device()) {
                        QuickWizardEntry.DEVICE_WATCH -> R.drawable.ic_watch
                        else                          -> R.drawable.ic_smartphone
                    }
                )
            }
            holder.buttonText.text = quickWizard[position].buttonText()
            holder.carbs.text = rh.gs(R.string.format_carbs, quickWizard[position].carbs())
        }

        override fun getItemCount(): Int = quickWizard.size()

        private inner class QuickWizardEntryViewHolder(itemView: View, var fragmentManager: FragmentManager) : RecyclerView.ViewHolder(itemView) {

            val buttonText: TextView = itemView.findViewById(R.id.overview_quickwizard_item_buttonText)
            val carbs: TextView = itemView.findViewById(R.id.overview_quickwizard_item_carbs)
            val from: TextView = itemView.findViewById(R.id.overview_quickwizard_item_from)
            val handleView: ImageView = itemView.findViewById(R.id.handleView)
            val device: ImageView = itemView.findViewById(R.id.overview_quickwizard_item_device)
            val to: TextView = itemView.findViewById(R.id.overview_quickwizard_item_to)
            private val editButton: Button = itemView.findViewById(R.id.overview_quickwizard_item_edit_button)
            private val removeButton: Button = itemView.findViewById(R.id.overview_quickwizard_item_remove_button)

            init {
                editButton.setOnClickListener {
                    val manager = fragmentManager
                    val editQuickWizardDialog = EditQuickWizardDialog()
                    val bundle = Bundle()
                    bundle.putInt("position", bindingAdapterPosition)
                    editQuickWizardDialog.arguments = bundle
                    editQuickWizardDialog.show(manager, "EditQuickWizardDialog")
                }
                removeButton.setOnClickListener {
                    quickWizard.remove(bindingAdapterPosition)
                    rxBus.send(EventQuickWizardChange())
                }
            }
        }

        fun moveItem(from: Int, to: Int) {
            Log.i("QuickWizard", "moveItem")
            quickWizard.move(from, to)
        }

        fun onDrop() {
            Log.i("QuickWizard", "onDrop")
            rxBus.send(EventQuickWizardChange())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = OverviewQuickwizardlistActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
}
