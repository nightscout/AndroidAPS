package app.aaps.plugins.automationstate.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.plugins.automationstate.R
import app.aaps.plugins.automationstate.databinding.AutomationStateFragmentBinding
import app.aaps.plugins.automationstate.databinding.AutomationStateItemBinding
import app.aaps.plugins.automationstate.dialogs.AutomationAddStateDialog
import app.aaps.plugins.automationstate.dialogs.AutomationStateValuesDialog
import app.aaps.plugins.automationstate.services.AutomationStateService
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AutomationStateFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var automationStateService: AutomationStateService
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers

    companion object {
        const val ID_MENU_ADD_STATE = 601
    }

    private val disposable = CompositeDisposable()
    private var _binding: AutomationStateFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        AutomationStateFragmentBinding.inflate(inflater, container, false).also { _binding = it }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerview.layoutManager = LinearLayoutManager(context)
        binding.recyclerview.adapter = StateAdapter()

        disposable += rxBus
            .toObservable(EventPreferenceChange::class.java)
            .debounce(1, TimeUnit.SECONDS)
            .observeOn(aapsSchedulers.main)
            .subscribe { updateUI() }

        updateUI()
        
        // Add menu provider
        requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposable.clear()
        _binding = null
    }

    private fun updateUI() {
        val states = automationStateService.getAllStates()
        (binding.recyclerview.adapter as? StateAdapter)?.updateStates()
        binding.noStateText.visibility = if (states.isEmpty()) View.VISIBLE else View.GONE
    }
    
    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_ADD_STATE, 0, rh.gs(R.string.add_automation_state))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_ADD_STATE -> {
                addState()
                true
            }
            else -> false
        }
        
    private fun addState() {
        AutomationAddStateDialog.newInstance().show(childFragmentManager, "AutomationAddStateDialog")
    }

    inner class StateAdapter : RecyclerView.Adapter<StateAdapter.StateViewHolder>() {

        private val states = mutableListOf<Pair<String, String>>()
        private val stateValues = mutableMapOf<String, List<String>>()

        init {
            updateStates()
        }

        fun updateStates() {
            states.clear()
            states.addAll(automationStateService.getAllStates())
            states.forEach { (stateName, _) ->
                stateValues[stateName] = automationStateService.getStateValues(stateName)
            }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StateViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.automation_state_item, parent, false)
            return StateViewHolder(view)
        }

        override fun onBindViewHolder(holder: StateViewHolder, position: Int) {
            val (stateName, currentState) = states[position]
            holder.binding.stateName.text = stateName
            
            // Clear previous state views
            val statesContainer = holder.binding.statesContainer as LinearLayout
            statesContainer.removeAllViews()
            
            // Create a horizontal layout for each row of states
            var currentRow = LinearLayout(holder.itemView.context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            statesContainer.addView(currentRow)
            
            var currentRowWidth = 0
            val screenWidth = resources.displayMetrics.widthPixels - 48 // Account for padding
            
            // Add all possible states
            stateValues[stateName]?.forEach { stateValue ->
                val stateView = TextView(holder.itemView.context).apply {
                    text = stateValue
                    setPadding(24, 16, 24, 16)
                    setTextAppearance(android.R.style.TextAppearance_Material_Body1)
                    
                    // Set background based on whether this is the current state
                    background = if (stateValue == currentState) {
                        resources.getDrawable(R.drawable.automation_state_active_background, null)
                    } else {
                        resources.getDrawable(R.drawable.automation_state_background, null)
                    }
                    
                    // Set click listener to set this value as the current state
                    setOnClickListener {
                        try {
                            automationStateService.setState(stateName, stateValue)
                            notifyDataSetChanged()
                            rxBus.send(EventPreferenceChange(rh.gs(R.string.automation_state_values)))
                        } catch (e: Exception) {
                            // Handle error
                        }
                    }
                }
                
                // Measure the view to get its width
                stateView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
                val stateViewWidth = stateView.measuredWidth + 16 // Add margin
                
                // Check if we need to create a new row
                if (currentRowWidth + stateViewWidth > screenWidth && currentRowWidth > 0) {
                    // Create a new row
                    currentRow = LinearLayout(holder.itemView.context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = 8
                        }
                    }
                    statesContainer.addView(currentRow)
                    currentRowWidth = 0
                }
                
                // Add margin between state views
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 8, 0)
                }
                currentRow.addView(stateView, params)
                currentRowWidth += stateViewWidth
            }

            // Set click listener to open the state values dialog
            holder.itemView.setOnClickListener {
                val dialog = AutomationStateValuesDialog.newInstance(stateName)
                dialog.show(childFragmentManager, "AutomationStateValuesDialog")
            }
        }

        override fun getItemCount(): Int = states.size

        inner class StateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val binding = AutomationStateItemBinding.bind(itemView)
        }
    }
} 