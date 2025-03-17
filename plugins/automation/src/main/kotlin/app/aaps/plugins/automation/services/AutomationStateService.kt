package app.aaps.plugins.automation.services

import app.aaps.core.interfaces.sharedPreferences.SP
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

//TODO: I'm hoping that this will make this an actual singleton
//I think the issue i was having, is that each automation would end up with their own instance of this class, and the state would end up out of sync ofcourse

@Singleton
class AutomationStateService @Inject constructor(
    private val sp: SP
) {

    private var automationStates: HashMap<String, String> = HashMap()
    private val spKey = "automation_state_service"

    init {
        val string = sp.getString(spKey, "{}")

        automationStates = Json.decodeFromString(string)
    }

    //check if we are in a particular state or not
    fun inState(stateName: String, state: String): Boolean {
        if (automationStates.containsKey(stateName.trim())) {
            return automationStates[stateName.trim()] == state.trim()
        }
        return false
    }

    fun setState(stateName: String, state: String) {
        automationStates[stateName.trim()] = state.trim()
        sp.putString(spKey, Json.encodeToString(automationStates))
    }
}
