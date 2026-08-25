package app.aaps.core.objects.wizard

import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlin.concurrent.Volatile

class QuickWizard(
    private val preferences: Preferences,
    // A plain factory, not a javax.inject.Provider: that type is JVM only and would pin this class
    // to one platform. Dagger still supplies it, as a method reference, from the Android side.
    private val quickWizardEntryProvider: () -> QuickWizardEntry
) {

    @Volatile private var storage: List<QuickWizardEntryData> = emptyList()

    private val _changes = MutableStateFlow(0)

    /**
     * Revision counter bumped on every entry-list change — a local edit OR a value synced from the
     * main phone ([StringNonKey.QuickWizard] is `Bidirectional`). Consumers observe this to refresh;
     * the in-memory cache is updated before each bump, so reading [list]/[get] right after observing
     * returns fresh data. Initial value `0` so it composes in `combine(...)` without blocking.
     */
    val changes: StateFlow<Int> = _changes.asStateFlow()

    // App-lifetime singleton: the subscription lives for the whole process, no cancellation needed.
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        storage = parse(preferences.get(StringNonKey.QuickWizard))
        setGuidsForOldEntries()
        // Keep the cache in lockstep with the persisted key. Covers edits from another screen and
        // master→client sync (applied via putRemote, which writes the key without going through save()).
        preferences.observe(StringNonKey.QuickWizard)
            .drop(1) // initial value already loaded above
            .onEach {
                storage = parse(it)
                _changes.update { v -> v + 1 }
            }
            .launchIn(scope)
    }

    /**
     * Reads the stored list, skipping anything unreadable rather than losing the whole list.
     *
     * An element that is not an object used to be a `ClassCastException` at construction time, which
     * meant a single damaged entry stopped the app from starting.
     */
    private fun parse(raw: String): List<QuickWizardEntryData> =
        runCatching {
            (Json.parseToJsonElement(raw) as JsonArray).mapNotNull { element ->
                (element as? JsonObject)?.let { QuickWizardEntryData.fromJsonObject(it) }
            }
        }.getOrDefault(emptyList())

    private fun setGuidsForOldEntries() {
        // for migration purposes; guid is a new required property
        val migrated = storage.map { if (it.guid == "") it.copy(guid = QuickWizardEntry.randomGuid()) else it }
        if (migrated == storage) return
        storage = migrated
        // Persist immediately. While the entries were live JSONObjects this wrote through into the
        // stored array and rode along on whatever save happened next; if none did, a DIFFERENT guid
        // was generated on every app start, so nothing could resolve a legacy entry by guid across
        // restarts. Saving here makes the migration stick the first time it runs.
        save()
    }

    fun getActive(): QuickWizardEntry? {
        for (i in storage.indices) {
            val entry = quickWizardEntryProvider().from(storage[i], i)
            if (entry.isActive()) return entry
        }
        return null
    }

    fun setData(newData: List<QuickWizardEntryData>) {
        storage = newData
    }

    fun save() {
        preferences.put(StringNonKey.QuickWizard, buildJsonArray { storage.forEach { add(it.toJsonObject()) } }.toString())
    }

    fun size(): Int = storage.size

    operator fun get(position: Int): QuickWizardEntry =
        quickWizardEntryProvider().from(storage[position], position)

    fun list(): ArrayList<QuickWizardEntry> =
        ArrayList<QuickWizardEntry>().also {
            for (i in 0 until size()) it.add(get(i))
        }

    fun get(guid: String): QuickWizardEntry? {
        for (i in storage.indices) {
            val entry = quickWizardEntryProvider().from(storage[i], i)
            if (entry.guid() == guid) {
                return entry
            }
        }
        return null
    }

    /**
     * Rearrange the entries in one shot. [order] is the new arrangement expressed as *current*
     * indices: `order[newIndex] == oldIndex`. It must be a permutation of the current indices — no
     * entry is added, removed or duplicated here.
     *
     * Takes the whole arrangement rather than repeated [move] calls because every [save] writes
     * [StringNonKey.QuickWizard], which is bidirectionally synced: a reorder session should cost one
     * round trip to the paired device, not one per step. An [order] that is already the identity
     * does not write at all.
     *
     * Entry order is user-visible — it is the order of the overview buttons — but nothing resolves
     * an entry *by position*: every consumer looks entries up by `guid`.
     *
     * @return true if the entries were rearranged, or were already in that order; false if [order]
     *         is not a permutation of the current indices (the list changed underneath the caller).
     */
    fun reorder(order: List<Int>): Boolean {
        val size = storage.size
        if (order.size != size || order.toSet() != (0 until size).toSet()) return false
        if (order.withIndex().all { (newIndex, oldIndex) -> newIndex == oldIndex }) return true
        storage = order.map { storage[it] }
        save()
        return true
    }

    fun newEmptyItem(): QuickWizardEntry {
        return quickWizardEntryProvider()
    }

    fun addOrUpdate(newItem: QuickWizardEntry) {
        // A position past the end appends. The old JSONArray.put(index, value) padded the list with
        // nulls up to the index instead, and a padded slot then broke the readers at the next app
        // start. A stale position is reachable: an entry can be read at index 2 and a shorter list
        // arrive over sync before it is written back.
        storage =
            if (newItem.position < 0 || newItem.position >= storage.size) storage + newItem.data
            else storage.toMutableList().also { it[newItem.position] = newItem.data }
        save()
    }

    fun remove(position: Int) {
        // Without the guard an out of range index changed nothing but still saved, pushing an
        // unchanged list through the sync channel.
        if (position < 0 || position >= storage.size) return
        storage = storage.toMutableList().also { it.removeAt(position) }
        save()
    }

}
