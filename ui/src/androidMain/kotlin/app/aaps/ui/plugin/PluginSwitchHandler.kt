package app.aaps.ui.plugin

import androidx.compose.runtime.Immutable
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The two confirmations a plugin enable/disable can raise, shared by every screen that can switch a plugin
 * (the Config Builder plugin list and global Search). Only display fields are exposed to Compose — the actual
 * target plugin is held privately in [PluginSwitchHandler] so this stays a true immutable snapshot.
 */
@Immutable
data class PluginSwitchDialogs(
    val hardwarePumpConfirmation: HardwarePumpConfirmation? = null,
    val pluginSwitchConfirmation: PluginSwitchConfirmation? = null
)

/** Safety gate shown the first time a real (non-virtual) pump is selected. [message] is the localized warning. */
@Immutable
data class HardwarePumpConfirmation(val message: String)

/**
 * Confirmation for switching the selection in a single-select category (pump, BG source, APS, …).
 * Because such a category always keeps exactly one plugin active, enabling [toName] implicitly disables
 * the currently-active [fromName]; the dialog makes that swap explicit before it is committed.
 */
@Immutable
data class PluginSwitchConfirmation(val fromName: String, val toName: String)

/**
 * Single source of truth for enabling/disabling a plugin, with the confirmation flow the UI needs:
 *  - single-select enable → "disable X, enable Y" swap confirmation (the category always keeps exactly one active);
 *  - first hardware-pump selection → the allow-hardware-pump safety gate;
 *  - everything else → immediate.
 *
 * Owned per-screen (Config Builder, Search). The owner passes its [scope] and an [onSwitched] callback that
 * refreshes its own view once a switch commits (Config Builder rebuilds its categories, Search re-runs its query).
 * Serializes state mutations through [switchMutex] and runs the blocking config work off the main thread, so the
 * locking + dispatcher contract is identical everywhere.
 */
class PluginSwitchHandler(
    private val scope: CoroutineScope,
    private val activePlugin: ActivePlugin,
    private val configBuilder: ConfigBuilder,
    private val onSwitched: () -> Unit,
    // Dispatcher for the blocking config work; overridable so tests can run it in virtual time.
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    private val switchMutex = Mutex()

    private val _dialogs = MutableStateFlow(PluginSwitchDialogs())
    val dialogs: StateFlow<PluginSwitchDialogs> = _dialogs.asStateFlow()

    // The pending target, kept out of the Compose-facing state. Reused across a swap confirmation and the
    // hardware-pump gate that may follow it (single-select hardware pump = both, in sequence).
    private data class Pending(val plugin: PluginBase, val type: PluginType, val enabled: Boolean)

    // A dialog is open awaiting confirmation (swap or hardware-pump). Read/written only on [scope]'s dispatcher.
    private var pending: Pending? = null

    // A switch flow is active: a confirmation dialog is open, or an execute() coroutine is running. Because all
    // handler state is confined to [scope]'s (single) dispatcher, this plain flag safely serializes the whole
    // toggle → (dialog) → confirm → execute sequence — a second tap while a flow is in progress is ignored
    // rather than racing/clobbering the shared [pending]/dialog state (see the concurrency review findings).
    private var busy = false

    /**
     * Enable/disable [plugin] for [type]. Single-select enabling always replaces the currently-active plugin, so
     * it raises the swap confirmation first; multi-select toggles and disables execute immediately. Ignored while
     * another switch flow is already in progress (a dialog is open or a switch is committing).
     */
    fun toggle(plugin: PluginBase, type: PluginType, enabled: Boolean) {
        if (busy || pending != null) return
        if (type.singleSelect && enabled) {
            // A single-select category always keeps exactly one plugin enabled, and the tapped plugin is the
            // disabled one, so the active plugin is simply the other enabled plugin of this type — the one this
            // switch replaces. (Guarded for the theoretically-impossible none-active case: just switch, no dialog.)
            val current = activePlugin.getSpecificPluginsList(type).firstOrNull { it.isEnabled(type) }
            if (current != null) {
                pending = Pending(plugin, type, enabled)
                _dialogs.update { it.copy(pluginSwitchConfirmation = PluginSwitchConfirmation(current.name, plugin.name)) }
                return
            }
        }
        execute(plugin, type, enabled)
    }

    fun confirmSwitch() {
        // Consume pending atomically so a double-tap of the confirm button can't fire the switch twice.
        val p = pending ?: return
        pending = null
        _dialogs.update { it.copy(pluginSwitchConfirmation = null) }
        execute(p.plugin, p.type, p.enabled)
    }

    fun dismissSwitch() {
        pending = null
        _dialogs.update { it.copy(pluginSwitchConfirmation = null) }
    }

    fun confirmHardwarePump() {
        // Consume pending atomically (double-tap safe) and complete the switch through the same commit path.
        val p = pending ?: return
        pending = null
        execute(p.plugin, p.type, p.enabled, confirmedHardwarePump = true)
    }

    fun dismissHardwarePump() {
        pending = null
        _dialogs.update { it.copy(hardwarePumpConfirmation = null) }
        onSwitched()
    }

    private fun execute(plugin: PluginBase, type: PluginType, enabled: Boolean, confirmedHardwarePump: Boolean = false) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                if (confirmedHardwarePump) {
                    runPluginSwitch { configBuilder.confirmPumpPluginSwitch(plugin, enabled, type) }
                    _dialogs.update { it.copy(hardwarePumpConfirmation = null) }
                    onSwitched()
                } else {
                    val warning = runPluginSwitch { configBuilder.requestPluginSwitch(plugin, enabled, type) }
                    if (warning != null) {
                        // Hardware-pump safety gate — remember the target so confirm can complete the switch.
                        pending = Pending(plugin, type, enabled)
                        _dialogs.update { it.copy(hardwarePumpConfirmation = HardwarePumpConfirmation(warning)) }
                    } else {
                        onSwitched()
                    }
                }
            } finally {
                busy = false
            }
        }
    }

    // Serialize plugin-state mutations (switchMutex) and move the blocking work off the main thread — both
    // requestPluginSwitch and confirmPumpPluginSwitch persist settings and run runBlocking pump-sync.
    private suspend fun <T> runPluginSwitch(block: suspend () -> T): T =
        switchMutex.withLock {
            withContext(ioDispatcher) {
                block()
            }
        }
}
