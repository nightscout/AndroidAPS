package app.aaps.plugins.automationstate.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.automationstate.R
import app.aaps.plugins.automationstate.databinding.AutomationStateDialogBinding
import app.aaps.plugins.automationstate.databinding.AutomationStateValueItemBinding
import app.aaps.plugins.automationstate.services.AutomationStateService
import dagger.android.support.DaggerDialogFragment
import javax.inject.Inject

class AutomationStateValuesDialog : DaggerDialogFragment() {

    @Inject lateinit var automationStateService: AutomationStateService
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus

    private var _binding: AutomationStateDialogBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: StateValuesAdapter
    private var stateName: String = ""
    private var stateValues: MutableList<String> = mutableListOf()
    private var currentStateValue: String = ""
    private var isNewState: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            stateName = it.getString("stateName", "")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = AutomationStateDialogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check if this is a new state
        isNewState = !automationStateService.hasStateValues(stateName)

        // Get the current state value
        val states = automationStateService.getAllStates()
        val stateEntry = states.find { it.first == stateName }
        currentStateValue = stateEntry?.second ?: ""

        // Set dialog title to include state name
        binding.dialogTitle.text = "${rh.gs(R.string.automation_state_values)}: $stateName"

        adapter = StateValuesAdapter { position ->
            if (stateValues.size > 1) {
                stateValues.removeAt(position)
                adapter.notifyItemRemoved(position)
            } else {
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.automation_state_last_value_cannot_be_deleted))
            }
        }

        binding.stateValuesList.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = this@AutomationStateValuesDialog.adapter
        }

        binding.addButton.setOnClickListener {
            val newValue = binding.newStateValue.text.toString().trim()
            if (newValue.isNotEmpty()) {
                stateValues.add(newValue)
                adapter.notifyItemInserted(stateValues.size - 1)
                binding.newStateValue.text.clear()
            } else {
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.enter_state_value))
            }
        }

        binding.okButton.setOnClickListener {
            if (stateValues.isEmpty()) {
                ToastUtils.showToastInUiThread(context, rh.gs(R.string.enter_state_value))
                return@setOnClickListener
            }
            
            // Save the state values
            automationStateService.setStateValues(stateName, stateValues)
            
            // If this is a new state or no current value, set the first value as current
            if (isNewState || currentStateValue.isEmpty()) {
                try {
                    automationStateService.setState(stateName, stateValues.first())
                } catch (e: Exception) {
                    ToastUtils.showToastInUiThread(context, e.message ?: "Error setting state")
                }
            }
            
            rxBus.send(EventPreferenceChange(rh.gs(R.string.automation_state_values)))
            dismiss()
        }

        binding.cancelButton.setOnClickListener {
            dismiss()
        }

        binding.deleteStateButton.setOnClickListener {
            context?.let { ctx ->
                OKDialog.showConfirmation(ctx, rh.gs(R.string.delete_state), rh.gs(R.string.delete_state_confirmation), 
                    Runnable {
                        // Delete the state and its values
                        automationStateService.deleteState(stateName)
                        rxBus.send(EventPreferenceChange(rh.gs(R.string.automation_state_values)))
                        dismiss()
                    })
            }
        }

        // Load existing values
        stateValues = automationStateService.getStateValues(stateName).toMutableList()
        adapter.notifyDataSetChanged()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    inner class StateValuesAdapter(private val onDelete: (Int) -> Unit) : 
        RecyclerView.Adapter<StateValuesAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = AutomationStateValueItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val stateValue = stateValues[position]
            holder.binding.stateValue.text = stateValue
            
            // Highlight the current active state value
            if (stateValue == currentStateValue) {
                holder.binding.stateValue.setBackgroundResource(R.drawable.automation_state_active_background)
            } else {
                holder.binding.stateValue.setBackgroundResource(R.drawable.automation_state_background)
            }
            
            // Set click listener to set this value as the current state
            holder.binding.stateValue.setOnClickListener {
                try {
                    automationStateService.setState(stateName, stateValue)
                    currentStateValue = stateValue
                    notifyDataSetChanged()
                    rxBus.send(EventPreferenceChange(rh.gs(R.string.automation_state_values)))
                } catch (e: Exception) {
                    ToastUtils.showToastInUiThread(context, e.message ?: "Error setting state")
                }
            }
            
            holder.binding.deleteButton.setOnClickListener {
                onDelete(position)
            }
        }

        override fun getItemCount() = stateValues.size

        inner class ViewHolder(val binding: AutomationStateValueItemBinding) : 
            RecyclerView.ViewHolder(binding.root)
    }

    companion object {
        fun newInstance(stateName: String): AutomationStateValuesDialog {
            return AutomationStateValuesDialog().apply {
                arguments = Bundle().apply {
                    putString("stateName", stateName)
                }
            }
        }
    }
}