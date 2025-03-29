package app.aaps.plugins.automationstate.services

import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.interfaces.sharedPreferences.SP
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationStateService  @Inject constructor(
    private val sp: SP
) : AutomationStateInterface {

    private var automationStates: HashMap<String, String> = HashMap()
    var stateValues: HashMap<String, List<String>> = HashMap()
        private set
    private val spKey = "automation_state_service"
    private val stateValuesKey = "automation_state_values"

    init {
        val string = sp.getString(spKey, "{}")
        try {
            automationStates = Json.decodeFromString(string)
        } catch (e: Exception) {
            automationStates = HashMap()
        }

        val valuesString = sp.getString(stateValuesKey, "{}")
        try {
            stateValues = Json.decodeFromString(valuesString)
        } catch (e: Exception) {
            stateValues = HashMap()
        }
    }

   override fun inState(stateName: String, state: String): Boolean {
        if (automationStates.containsKey(stateName.trim())) {
            return automationStates[stateName.trim()] == state.trim()
        }
        return false
    }

   override fun setState(stateName: String, state: String) {
        val trimmedName = stateName.trim()
        val trimmedState = state.trim()
        
        // Validate that the state value is in the allowed list
       require(stateValues.containsKey(trimmedName) ) { "Invalid state name: $trimmedName" }
       require(stateValues[trimmedName]!!.contains(trimmedState)) { "Invalid state value: $trimmedState" }
        
        automationStates[trimmedName] = trimmedState
        sp.putString(spKey, Json.encodeToString(automationStates))
    }

   override fun getAllStates(): List<Pair<String, String>> {
        return automationStates.toList()
    }

    fun clearStates() {
        automationStates.clear()
        sp.putString(spKey, "{}")
    }

   override fun getStateValues(stateName: String): List<String> {
        return stateValues[stateName.trim()] ?: emptyList()
    }

   override fun setStateValues(stateName: String, values: List<String>) {
        val trimmedName = stateName.trim()
        val trimmedValues = values.map { it.trim() }
        
        // If there's a current state value that's not in the new values list,
        // clear the current state
        val currentState = automationStates[trimmedName]
        if (currentState != null && !trimmedValues.contains(currentState)) {
            automationStates.remove(trimmedName)
            sp.putString(spKey, Json.encodeToString(automationStates))
        }
        
        stateValues[trimmedName] = trimmedValues
        sp.putString(stateValuesKey, Json.encodeToString(stateValues))
    }

   override fun hasStateValues(stateName: String): Boolean {
        return stateValues.containsKey(stateName.trim())
    }

   override fun deleteState(stateName: String) {
        val trimmedName = stateName.trim()
        automationStates.remove(trimmedName)
        stateValues.remove(trimmedName)
        sp.putString(spKey, Json.encodeToString(automationStates))
        sp.putString(stateValuesKey, Json.encodeToString(stateValues))
    }
}
