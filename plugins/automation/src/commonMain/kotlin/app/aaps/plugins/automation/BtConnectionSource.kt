package app.aaps.plugins.automation

import app.aaps.core.interfaces.rx.events.EventBTChange

/**
 * Read-only access to the Bluetooth connect/disconnect events the automation runtime has buffered
 * since its last evaluation. Implemented by [AutomationRuntime]; injected by
 * [app.aaps.plugins.automation.triggers.TriggerBTDevice] so the trigger doesn't depend on the
 * concrete runtime.
 *
 * Module-internal by design: the buffer has a single reader (the BT trigger). The shared
 * [EventBTChange] RxBus event is consumed independently elsewhere (e.g. the DanaR service); this is
 * only the automation-specific "edges seen since the last poll" view.
 */
interface BtConnectionSource {

    /** Snapshot of BT connect/disconnect events seen since the last `processActions()` drain. */
    fun recentBtConnects(): List<EventBTChange>
}
