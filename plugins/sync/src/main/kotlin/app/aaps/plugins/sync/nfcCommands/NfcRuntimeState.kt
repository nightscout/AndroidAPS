package app.aaps.plugins.sync.nfcCommands

import javax.inject.Inject
import javax.inject.Singleton

/**
 * State that outlives a single [app.aaps.plugins.sync.nfcCommands.actions.NfcAction] but belongs to no
 * single one of them.
 *
 * Both members used to sit on `NfcCommandsPlugin`, which meant every action had to be handed the whole
 * plugin to reach them. They are here so an action can ask for this and nothing else.
 *
 * The action state map is deliberately still `Any` typed - a sealed type for it is its own change, kept
 * separate from moving it out of the plugin.
 */
@Singleton
class NfcRuntimeState @Inject constructor() {

    /**
     * When the last bolus was delivered through NFC, used for the remote bolus cooldown that
     * `Constants.remoteBolusMinDistance` defines. Shared because the wizard and the plain bolus must
     * respect one another's deliveries.
     */
    var lastRemoteBolusTime: Long = 0

    /** Values handed from one phase of an action to the next, for example a wizard calculation. */
    private val actionStates = mutableMapOf<String, Any>()

    fun setActionState(key: String, state: Any) {
        actionStates[key] = state
    }

    fun getActionState(key: String): Any? = actionStates[key]

    /** Called before a chain starts and after it finishes, so nothing leaks between scans. */
    fun clearActionStates() {
        actionStates.clear()
    }
}
