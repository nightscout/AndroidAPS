package app.aaps.plugins.sync.nsclientV3.clientcontrol

import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.interfaces.BooleanNonPreferenceKey
import app.aaps.core.keys.interfaces.DoubleNonPreferenceKey
import app.aaps.core.keys.interfaces.IntNonPreferenceKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.core.keys.interfaces.UnitDoublePreferenceKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generic client-side publisher for bidirectionally-synced preference edits (plain values AND the
 * definition blobs — QuickWizard/Scenes/Insulin/etc. — which persist as synced string prefs). On an
 * AAPSCLIENT device it collects [Preferences.syncedLocalChanges] (emitted only on LOCAL `put`s to
 * `Bidirectional` keys), batches the changed keys over a short settle window, and pushes them to the
 * master through the **confirmed round-trip** ([ClientControlRoundTrip.run]) — which
 * shows a pending modal and resolves on the master's ACK. The master applies per-key last-writer-wins
 * and republishes via the running-config doc.
 *
 * Why batched + sequential: one round-trip at a time (the collector suspends inside `ClientControlRoundTrip.run`
 * until the modal resolves), so edits made meanwhile accumulate in [pending] and ship in the next
 * round-trip. That sidesteps the single-in-flight contention and the shared `preferences_update`
 * identifier — there is never more than one pref round-trip outstanding. The settle window also lets a
 * slider drag finish before the modal appears.
 *
 * No echo: applied-from-sync writes go through `Preferences.putRemote`, never emitted on
 * [Preferences.syncedLocalChanges]. Only genuine user edits reach here (programmatic synced-key writes
 * on a client were eliminated — see MainApp migrations — so the modal only ever shows for real edits).
 *
 * Adding a new bidirectional setting requires no change here — it just shows up as another key.
 *
 * UnitDouble values are transmitted RAW (mg/dL) via [Preferences.getRaw], not in the user's display
 * unit: the persisted/canonical form is always mg/dL and the display-unit conversion is a presentation
 * concern. Shipping raw keeps client and master unit-agnostic over the wire — the master stores exactly
 * what it receives and each side renders in its own unit, so a unit mismatch between the two devices can
 * never corrupt the stored value.
 */
@OptIn(FlowPreview::class)
@Singleton
class PreferencesClientPublisher @Inject constructor(
    private val preferences: Preferences,
    private val clientControlRoundTrip: ClientControlRoundTrip,
    private val config: Config,
    private val rh: ResourceHelper,
    private val aapsLogger: AAPSLogger
) {

    // @Volatile: start()/stop() may be called from different threads (lifecycle vs UI); the read in
    // start()'s `if (job != null)` guard must see a write from stop() on another thread.
    @Volatile private var job: Job? = null

    // Keys changed since the last round-trip, drained on each debounced trigger.
    private val pending = mutableSetOf<NonPreferenceKey>()

    fun start(scope: CoroutineScope) {
        if (!config.AAPSCLIENT) return
        if (job != null) return
        job = scope.launch {
            preferences.syncedLocalChanges
                .onEach { key -> synchronized(pending) { pending.add(key) } }
                .debounce(SETTLE_MS)
                .collect {
                    val batch = synchronized(pending) { pending.toList().also { pending.clear() } }
                    val changes = batch.mapNotNull { key -> serialize(key)?.let { key.key to it } }.toMap()
                    if (changes.isEmpty()) return@collect
                    aapsLogger.debug(LTag.NSCLIENT, "ClientControl: preferences.update round-trip keys=${changes.keys}")
                    // Suspends until the round-trip resolves (drives the single app-level modal); further
                    // edits queue in `pending` and ship next — never two concurrent pref round-trips.
                    clientControlRoundTrip.run(
                        ClientControlActionDispatcher.Command.PreferenceEdit(changes),
                        rh.gs(app.aaps.core.ui.R.string.clientcontrol_action_update_settings)
                    )
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * (serialized value, lastModified) for a synced key, or null for an unsupported type.
     *
     * Known-benign read race: the value and its SyncedPrefModified timestamp are read in two separate
     * `preferences.get` calls, so a concurrent edit between them could pair a new value with an old
     * timestamp (or vice-versa). Not guarded — the master applies per-key last-writer-wins keyed on the
     * timestamp, and the very next edit re-emits the key with a consistent (value, timestamp), so any
     * transient mismatch self-corrects on the master without a lost write.
     */
    private fun serialize(key: NonPreferenceKey): Pair<String, Long>? {
        val value = when (key) {
            is BooleanNonPreferenceKey -> preferences.get(key).toString()
            is StringNonPreferenceKey  -> preferences.get(key)
            is IntNonPreferenceKey     -> preferences.get(key).toString()
            is DoubleNonPreferenceKey  -> preferences.get(key).toString()   // raw (DoubleNonPreferenceKey getter)
            is UnitDoublePreferenceKey -> preferences.getRaw(key).toString()   // raw mg/dl, 1:1

            else                       -> {
                aapsLogger.warn(LTag.NSCLIENT, "ClientControl: preferences.update unsupported key type for ${key.key}, skipping")
                return null
            }
        }
        return value to preferences.get(LongComposedKey.SyncedPrefModified, key.key)
    }

    private companion object {

        // Settle window before a round-trip fires: collapses a slider drag / a burst of edits into one
        // batched, confirmed round-trip (one modal), short enough to still feel responsive.
        private const val SETTLE_MS = 500L
    }
}
