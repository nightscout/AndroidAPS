package app.aaps.cgm.dexcomg7.data

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Preference keys of this driver. Not exported: the state holds the pairing code and the key. */
enum class G7StringNonKey(
    override val key: String,
    override val defaultValue: String,
    override val exportable: Boolean = false
) : StringNonPreferenceKey {

    State("dexcom_g7_direct_state", "")
}

/**
 * Holds [G7State] in memory and in preferences. Every change is saved at once, because the key and
 * the sensor's address must survive the app being killed between two readings.
 */
@SingleIn(AppScope::class)
@Inject
class G7StateStore(
    private val preferences: Preferences,
    private val aapsLogger: AAPSLogger
) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Any()
    private val _state = MutableStateFlow(load())
    val state: StateFlow<G7State> = _state.asStateFlow()

    val value: G7State get() = _state.value

    fun update(change: (G7State) -> G7State): G7State = synchronized(lock) {
        val updated = change(_state.value)
        if (updated != _state.value) {
            _state.value = updated
            preferences.put(G7StringNonKey.State, json.encodeToString(G7State.serializer(), updated))
        }
        updated
    }

    /** Reads the stored state again, for example after a settings import. */
    fun reload() = synchronized(lock) { _state.value = load() }

    private fun load(): G7State {
        val raw = preferences.get(G7StringNonKey.State)
        if (raw.isBlank()) return G7State()
        return runCatching { json.decodeFromString(G7State.serializer(), raw) }
            .onFailure { aapsLogger.error(LTag.BGSOURCE, "Dexcom G7: stored state could not be read, starting empty", it) }
            .getOrDefault(G7State())
    }
}
