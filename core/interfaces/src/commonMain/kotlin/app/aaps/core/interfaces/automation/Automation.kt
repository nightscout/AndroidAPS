package app.aaps.core.interfaces.automation

import kotlinx.coroutines.flow.StateFlow

interface Automation {

    /**
     * Live snapshot of the automation event list. Emits on user edits, ordering changes, and
     * NS-synced reloads. Replaces the prior `EventAutomationDataChanged` RxBus broadcast.
     */
    val events: StateFlow<List<AutomationEvent>>

    /**
     * Single source of truth for "automation executes here". True only on a master device; clients
     * (AAPSCLIENT) edit + sync definitions but never run them. UI surfaces that offer to run/execute
     * a user action must hide it when this is false. Execution itself is also hard-gated internally.
     */
    val executionEnabled: Boolean

    fun findEventById(id: String): AutomationEvent?
    suspend fun processEvent(someEvent: AutomationEvent)

    /**
     * Create new Automation event to remind the user to bolus
     */
    fun scheduleAutomationEventBolusReminder()

    /**
     * Remove scheduled reminder from automations
     *
     */
    fun removeAutomationEventBolusReminder()

    /**
     * Remove Automation event
     */
    fun removeAutomationEventEatReminder()

    /**
     * Create new Automation event to alarm when is time to eat
     */
    fun scheduleAutomationEventEatReminder()
}