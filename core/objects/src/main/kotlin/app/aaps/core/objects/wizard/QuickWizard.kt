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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class QuickWizard @Inject constructor(
    private val preferences: Preferences,
    private val quickWizardEntryProvider: Provider<QuickWizardEntry>
) {

    @Volatile private var storage = JSONArray()

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
        storage = JSONArray(preferences.get(StringNonKey.QuickWizard))
        setGuidsForOldEntries()
        // Keep the cache in lockstep with the persisted key. Covers edits from another screen and
        // master→client sync (applied via putRemote, which writes the key without going through save()).
        preferences.observe(StringNonKey.QuickWizard)
            .drop(1) // initial value already loaded above
            .onEach {
                storage = JSONArray(it)
                _changes.update { v -> v + 1 }
            }
            .launchIn(scope)
    }

    private fun setGuidsForOldEntries() {
        // for migration purposes; guid is a new required property
        for (i in 0 until storage.length()) {
            val entry = quickWizardEntryProvider.get().from(storage.get(i) as JSONObject, i)
            if (entry.guid() == "") {
                val guid = UUID.randomUUID().toString()
                entry.storage.put("guid", guid)
            }
        }
    }

    fun getActive(): QuickWizardEntry? {
        for (i in 0 until storage.length()) {
            val entry = quickWizardEntryProvider.get().from(storage.get(i) as JSONObject, i)
            if (entry.isActive()) return entry
        }
        return null
    }

    fun setData(newData: JSONArray) {
        storage = newData
    }

    fun save() {
        preferences.put(StringNonKey.QuickWizard, storage.toString())
    }

    fun size(): Int = storage.length()

    operator fun get(position: Int): QuickWizardEntry =
        quickWizardEntryProvider.get().from(storage.get(position) as JSONObject, position)

    fun list(): ArrayList<QuickWizardEntry> =
        ArrayList<QuickWizardEntry>().also {
            for (i in 0 until size()) it.add(get(i))
        }

    fun get(guid: String): QuickWizardEntry? {
        for (i in 0 until storage.length()) {
            val entry = quickWizardEntryProvider.get().from(storage.get(i) as JSONObject, i)
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
        val size = storage.length()
        if (order.size != size || order.toSet() != (0 until size).toSet()) return false
        if (order.withIndex().all { (newIndex, oldIndex) -> newIndex == oldIndex }) return true
        val reordered = JSONArray()
        order.forEach { reordered.put(storage.get(it)) }
        storage = reordered
        save()
        return true
    }

    fun newEmptyItem(): QuickWizardEntry {
        return quickWizardEntryProvider.get()
    }

    fun addOrUpdate(newItem: QuickWizardEntry) {
        if (newItem.position == -1)
            storage.put(newItem.storage)
        else
            storage.put(newItem.position, newItem.storage)
        save()
    }

    fun remove(position: Int) {
        storage.remove(position)
        save()
    }

}
