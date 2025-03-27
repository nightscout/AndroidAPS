package app.aaps.ui.activities

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.automation.Automation
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.pump.defs.determineCorrectBolusStepSize
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.objects.wizard.QuickWizard
import app.aaps.core.objects.wizard.QuickWizardEntry
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.dragHelpers.ItemTouchHelperAdapter
import app.aaps.core.ui.dragHelpers.OnStartDragListener
import app.aaps.core.ui.dragHelpers.SimpleItemTouchHelperCallback
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.ui.R
import app.aaps.ui.databinding.ActivityQuickwizardListBinding
import app.aaps.ui.databinding.QuickwizardListItemBinding
import app.aaps.ui.dialogs.EditQuickWizardDialog
import app.aaps.ui.events.EventQuickWizardChange
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import kotlin.math.abs

class QuickWizardListActivity : TranslatedDaggerAppCompatActivity(), OnStartDragListener {

    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var quickWizard: QuickWizard
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var iobCobCalculator: IobCobCalculator
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var automation: Automation
    @Inject lateinit var uiInteraction: UiInteraction

    private var disposable: CompositeDisposable = CompositeDisposable()
    private lateinit var actionHelper: ActionModeHelper<QuickWizardEntry>
    private val itemTouchHelper = ItemTouchHelper(SimpleItemTouchHelperCallback())
    private lateinit var binding: ActivityQuickwizardListBinding

    override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
        itemTouchHelper.startDrag(viewHolder)
    }

    private inner class RecyclerViewAdapter(var fragmentManager: FragmentManager) : RecyclerView.Adapter<RecyclerViewAdapter.QuickWizardEntryViewHolder>(), ItemTouchHelperAdapter {

        private inner class QuickWizardEntryViewHolder(val binding: QuickwizardListItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickWizardEntryViewHolder {
            val binding = QuickwizardListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return QuickWizardEntryViewHolder(binding)
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: QuickWizardEntryViewHolder, position: Int) {
            val entry = quickWizard[position]
            holder.binding.from.text = dateUtil.timeString(entry.validFromDate())
            holder.binding.to.text = dateUtil.timeString(entry.validToDate())
            holder.binding.buttonText.text = entry.buttonText()
            var bindingCarbsTextFull = rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs())
            if (entry.useEcarbs() == QuickWizardEntry.YES) {
                bindingCarbsTextFull += " +" + rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs2())
                bindingCarbsTextFull += "/" + entry.duration() + "h->" + entry.time() + "min"
            }
            holder.binding.carbs.text = bindingCarbsTextFull
            if (entry.device() == QuickWizardEntry.DEVICE_ALL) {
                holder.binding.device.visibility = View.GONE
            } else {
                holder.binding.device.visibility = View.VISIBLE
                holder.binding.device.setImageResource(
                    when (quickWizard[position].device()) {
                        QuickWizardEntry.DEVICE_WATCH -> app.aaps.core.objects.R.drawable.ic_watch
                        else                          -> app.aaps.core.objects.R.drawable.ic_smartphone
                    }
                )
                holder.binding.device.contentDescription = when (quickWizard[position].device()) {
                    QuickWizardEntry.DEVICE_WATCH -> rh.gs(R.string.a11y_only_on_watch)
                    else                          -> rh.gs(R.string.a11y_only_on_phone)
                }
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
            holder.binding.root.setOnLongClickListener {
                if (actionHelper.isNoAction) {
                    val actualBg = iobCobCalculator.ads.actualBg()
                    val profile = profileFunction.getProfile()
                    val profileName = profileFunction.getProfileName()
                    val pump = activePlugin.activePump
                    val quickWizardEntry = quickWizard[position]

                    if (actualBg != null && profile != null) {
                        val wizard = quickWizardEntry.doCalc(profile, profileName, actualBg)

                        if (wizard.calculatedTotalInsulin > 0.0 && quickWizardEntry.carbs() > 0.0) {
                            val carbsAfterConstraints = constraintChecker.applyCarbsConstraints(ConstraintObject(quickWizardEntry.carbs(), aapsLogger)).value()
                            if (abs(wizard.insulinAfterConstraints - wizard.calculatedTotalInsulin) >= pump.pumpDescription.pumpType.determineCorrectBolusStepSize(wizard.insulinAfterConstraints) || carbsAfterConstraints != quickWizardEntry.carbs()) {
                                OKDialog.show(
                                    it.context, rh.gs(app.aaps.core.ui.R.string.treatmentdeliveryerror), rh.gs(R.string.constraints_violation) + "\n" + rh.gs(
                                        R.string
                                            .change_your_input
                                    )
                                )
                            }
                            wizard.confirmAndExecute(it.context, quickWizardEntry)
                        }
                    }
                    return@setOnLongClickListener true
                }
                false
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
        binding = ActivityQuickwizardListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        actionHelper = ActionModeHelper(rh, this, null)
        actionHelper.setUpdateListHandler { binding.recyclerview.adapter?.notifyDataSetChanged() }
        actionHelper.setOnRemoveHandler { removeSelected(it) }
        actionHelper.enableSort = true

        title = rh.gs(app.aaps.core.ui.R.string.quickwizard)
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
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(app.aaps.core.objects.R.menu.menu_actions, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                actionHelper.onOptionsItemSelected(menuItem)
        })
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
        OKDialog.showConfirmation(this, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), Runnable {
            //fix for bug with removal of QuickWizardEntries. Everytime and item is deleted you have to shift the position of to-be-deleted QW to left
            var shiftPositionToLeftFor = 0
            selectedItems.forEach { _, item ->
                quickWizard.remove(item.position - shiftPositionToLeftFor)
                shiftPositionToLeftFor++
                rxBus.send(EventQuickWizardChange())
            }
            actionHelper.finish()
        })
    }

    private fun getConfirmationText(selectedItems: SparseArray<QuickWizardEntry>): String {
        if (selectedItems.size() == 1) {
            val entry = selectedItems.valueAt(0)
            return "${rh.gs(app.aaps.core.ui.R.string.remove_button)} ${entry.buttonText()} ${rh.gs(app.aaps.core.objects.R.string.format_carbs, entry.carbs())}\n" +
                "${dateUtil.timeString(entry.validFromDate())} - ${dateUtil.timeString(entry.validToDate())}"
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size())
    }

}
