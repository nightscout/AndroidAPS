package app.aaps.implementation.insulin

import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.insulin.InsulinManager.UpdateResult
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.fromJsonObject
import app.aaps.core.objects.extensions.toJsonObject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.concurrent.Volatile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

/**
 * Created by Philoul on 29.12.2024.
 */

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class InsulinImpl(
    private val preferences: Preferences,
    val rh: TextResolver,
    val profileFunction: ProfileFunction,
    val aapsLogger: AAPSLogger,
    val config: Config,
    val hardLimits: HardLimits,
    val uel: UserEntryLogger,
    // Plain CoroutineScope, not @ApplicationScope: that qualifier is javax and cannot appear in
    // commonMain. AppCoroutineBindings.unqualifiedAppScope binds the very same scope without it.
    private val appScope: CoroutineScope
) : InsulinManager {

    // Replaces `@Synchronized` on every method that touches [insulins] or persists it. Those all locked
    // `this`, and nothing outside ever locked this object, so one lock is the same guard. It is reentrant,
    // which these need - addNewInsulin() calls storeSettings(), and adoptExternalChange() calls bootstrap().
    private val lock = AapsLock()

    // Never changed in place once it is published here: every change - a reload, an edit - builds a new
    // list and swaps it in. A reload runs from a background scope, and the pickers read (and iterate) this
    // on the UI thread without the lock, so an in-place change could show them an empty or half-built
    // catalogue, or throw ConcurrentModificationException. An activation wizard takes `firstOrNull()` of it.
    @Volatile override var insulins: ArrayList<ICfg> = ArrayList()

    // Serialized config of the most recent LOCAL edit (storeSettings). Stamped BEFORE preferences.put() at
    // the single store choke point so a UI observing InsulinConfiguration can recognize its own echo
    // regardless of dispatch timing (Main.immediate can deliver the change re-entrantly inside put()).
    // Edits only, as the interface says: a normalization after an external change must still look external
    // to that UI. @Volatile: written under the store lock, read from the UI (main) thread without it.
    @Volatile override var lastStoredConfiguration: String = ""

    // The stored value that [insulins] was last built from or written to by this class itself: an edit, a
    // normalization, or an adoption below. The observer in init compares against it, which skips the
    // echoes of this class's own writes. It has to include adoptions: marking only this class's own
    // writes would skip a legitimate change back to an earlier value (the master writes X, a client pushes
    // Y, a client pushes X again). The public [loadSettings] does not update it - the insulin screen calls
    // that verbatim, and the master's own adoption must still run afterwards to normalize.
    @Volatile private var lastKnownConfiguration: String = ""

    init {
        bootstrap()
        // Adopt InsulinConfiguration written by anyone else: on a client the master's push, on the master
        // a client's push (the cold-key bidirectional sync applies it with putRemote). A settings import
        // does not come here today: it writes below Preferences, so the flow does not emit (issue #5140).
        // The insulin pickers - the activation wizards, fill, insulin switch, profile management -
        // all read [insulins] and never reload it themselves. The master reload that used to do this went
        // with the move to the generic sync channel; since then only the insulin screen's view model kept
        // the list current, and only while the main UI was alive and had no unsaved insulin edit.
        preferences.observe(StringNonKey.InsulinConfiguration).drop(1)
            .onEach { value -> if (value != lastKnownConfiguration) adoptExternalChange() }
            .launchIn(appScope)
    }

    /**
     * Someone else wrote the value. Handled like the stored value at startup (see [bootstrap]): a client
     * mirrors it verbatim, the master normalizes it, because it may come from a client on another app
     * version or from an older backup.
     *
     * Reads the stored value again under the lock instead of using the one that woke the observer up. A
     * local edit may have been stored in between; it wins, and it is already in [insulins].
     */
    private fun adoptExternalChange(): Unit = lock.withLock {
        if (preferences.get(StringNonKey.InsulinConfiguration) != lastKnownConfiguration) bootstrap()
    }


    override fun insulinTemplateList(): List<InsulinType> = listOf(
        InsulinType.OREF_RAPID_ACTING,
        InsulinType.OREF_ULTRA_RAPID_ACTING,
        InsulinType.OREF_LYUMJEV,
        InsulinType.OREF_FREE_PEAK
    )

    override fun concentrationList(): List<ConcentrationType> = listOf(
        ConcentrationType.U10,
        ConcentrationType.U50,
        ConcentrationType.U100,
        ConcentrationType.U200
    )

    override fun addNewInsulin(newICfg: ICfg, ue: Boolean, keepName: Boolean): ICfg = lock.withLock {
        val updated = ArrayList(insulins)
        val newInsulin = addTo(updated, newICfg, ue, keepName)
        insulins = updated
        storeSettings()
        newInsulin
    }

    override fun updateInsulin(originalLabel: String, edited: ICfg): UpdateResult = lock.withLock {
        val index = insulins.indexOfFirst { it.insulinLabel == originalLabel }
        if (index < 0) return@withLock UpdateResult.NotFound
        val nickname = edited.insulinNickname.ifBlank { rh.gs(InsulinType.fromPeak(edited.insulinPeakTime).label) }
        val label = uniqueName(insulins, nickname, edited.peak, edited.dia, edited.concentration, excludeIndex = index)
        // uniqueName gives up after 100 numbered tries, so the name can still be taken.
        if (insulins.withIndex().any { (i, insulin) -> i != index && insulin.insulinLabel == label })
            return@withLock UpdateResult.LabelTaken(label)
        val updated = ArrayList(insulins)
        updated[index] = deepClone(edited).also {
            it.insulinLabel = label
            it.insulinNickname = nickname
        }
        insulins = updated
        uel.log(Action.STORE_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(label))
        storeSettings()
        UpdateResult.Updated(label)
    }

    // Fills the nickname and a label that is unique within [list], then appends a copy to [list].
    // Does not persist. [list] is always a new list that the caller swaps in afterwards.
    private fun addTo(list: ArrayList<ICfg>, newICfg: ICfg, ue: Boolean, keepName: Boolean = false): ICfg {
        val template = InsulinType.fromPeak(newICfg.insulinPeakTime)
        val nickname = newICfg.insulinNickname.ifBlank { rh.gs(template.label) }
        val fullName = uniqueName(
            list = list,
            nickname = nickname,
            peak = newICfg.peak,
            dia = newICfg.dia,
            concentration = newICfg.concentration,
            excludeIndex = -1
        )
        newICfg.insulinLabel = if (keepName) newICfg.insulinLabel.ifBlank { fullName } else fullName
        newICfg.insulinNickname = nickname
        val newInsulin = deepClone(newICfg)
        list.add(newInsulin)
        if (ue) {
            uel.log(Action.NEW_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(fullName))
        }
        return newInsulin
    }

    override fun removeInsulin(label: String) = lock.withLock {
        if (insulins.size <= 1) return@withLock // the catalogue is never emptied — the pickers must always have something to offer
        val index = insulins.indexOfFirst { it.insulinLabel == label }
        if (index < 0) return@withLock
        insulins = ArrayList(insulins).also { it.removeAt(index) }
        uel.log(Action.INSULIN_REMOVED, Sources.Insulin, value = ValueWithUnit.SimpleString(label))
        storeSettings()
    }

    override fun buildSuffix(peak: Int, dia: Double, concentration: Double): String {
        val concLabel = rh.gs(ConcentrationType.fromDouble(concentration).label)
        val diaLabel = if (dia % 1.0 == 0.0) "${dia.toInt()}h" else "${dia}h"
        return "${peak}m $diaLabel $concLabel"
    }

    override fun buildFullName(nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int): String =
        uniqueName(insulins, nickname, peak, dia, concentration, excludeIndex)

    private fun uniqueName(list: List<ICfg>, nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int): String {
        val suffix = buildSuffix(peak, dia, concentration)
        val existingNames = list.mapIndexed { idx, it ->
            if (idx == excludeIndex) null else it.insulinLabel
        }.filterNotNull()
        val full = "$nickname $suffix".trim()
        var candidate = full
        var counter = 1
        while (existingNames.any { it == candidate } && counter <= 100) {
            candidate = "$nickname ($counter) $suffix".trim()
            counter++
        }
        return candidate
    }

    override fun buildDisplaySuffix(nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int): String {
        val fullName = buildFullName(nickname, peak, dia, concentration, excludeIndex)
        return fullName.removePrefix(nickname).trim()
    }

    override fun insulinAlreadyExists(iCfg: ICfg, excludeIndex: Int): Boolean = containsEqual(insulins, iCfg, excludeIndex)

    private fun containsEqual(list: List<ICfg>, iCfg: ICfg, excludeIndex: Int): Boolean {
        list.forEachIndexed { index, insulin ->
            if (index != excludeIndex) {
                if (iCfg.isEqual(insulin)) {
                    return true
                }
            }
        }
        return false
    }

    override fun insulinIndex(iCfg: ICfg?): Int {
        insulins.forEachIndexed { index, insulin ->
            if (insulin.isEqual(iCfg)) {
                return index
            }
        }
        return -1
    }

    // Verbatim mirror of the persisted config — parse only, NO normalization, NO store. Normalization
    // happens in bootstrap (at init, and on the master when someone else wrote the value) and at edit time
    // (addNewInsulin, updateInsulin); a master push is already normalized, so applying it is a pure re-parse → no
    // re-store → no echo loop.
    override fun loadSettings(): Unit = lock.withLock { parse(preferences.get(StringNonKey.InsulinConfiguration)) }

    // Builds the new list first and then swaps it in (see [insulins]).
    private fun parse(value: String) {
        val loaded = ArrayList<ICfg>()
        val insulinArray = runCatching {
            (Json.parseToJsonElement(value) as? JsonObject)?.get("insulin") as? JsonArray
        }.getOrNull()
        insulinArray?.forEach { element ->
            runCatching { (element as? JsonObject)?.let { loaded.add(ICfg.fromJsonObject(it)) } }
        }
        insulins = loaded
    }

    // At init, and whenever someone else wrote the value (see adoptExternalChange).
    // CLIENT: mirror the master's config verbatim, seeding a normalized default ONLY when empty (so the
    // pickers always have something to offer). The master owns the canonical form, so client never
    // re-canonicalizes non-empty data — avoids cosmetically diverging from a master that serializes
    // slightly differently (mixed app versions).
    // MASTER: normalize legacy data (fill nicknames, dedup, regenerate labels, seed default) and persist
    // the canonical form once.
    // Either branch persists via putRemote: no client→master echo, stamp floored to the current value.
    private fun bootstrap(): Unit = lock.withLock {
        if (config.AAPSCLIENT) {
            val stored = preferences.get(StringNonKey.InsulinConfiguration)
            parse(stored) // verbatim — master data stays untouched
            lastKnownConfiguration = stored
            if (insulins.isEmpty()) {
                insulins = ArrayList<ICfg>().also { addTo(it, InsulinType.OREF_RAPID_ACTING.getICfg(rh), ue = true) }
                persistBootstrap()
            }
        } else {
            val before = preferences.get(StringNonKey.InsulinConfiguration)
            val jsonObject = runCatching { Json.parseToJsonElement(before) as? JsonObject }.getOrNull()
            applyConfiguration(jsonObject ?: buildJsonObject {})
            if (configuration().toString() != before) persistBootstrap()
            else lastKnownConfiguration = before
        }
    }

    /** Persist the bootstrapped config via putRemote — no echo, stamp floored to the current value. */
    private fun persistBootstrap() {
        val cfg = configuration().toString()
        // Not lastStoredConfiguration: this is not an edit (see its comment).
        lastKnownConfiguration = cfg
        preferences.putRemote(
            StringNonKey.InsulinConfiguration, cfg,
            preferences.get(LongComposedKey.SyncedPrefModified, StringNonKey.InsulinConfiguration.key)
        )
    }

    private fun storeSettings(): Unit = lock.withLock {
        // Genuine edit → local put. The normalization in bootstrap never comes here: it persists once, at
        // the end, via putRemote. The generic sync layer stamps SyncedPrefModified and signals the
        // client→master publisher on this write; no manual version bump needed.
        // Stamp lastStoredConfiguration BEFORE the put so an observer that receives the change
        // synchronously (Main.immediate re-entrancy inside put()) already sees the new value and can
        // suppress this self-echo.
        val cfg = configuration().toString()
        lastStoredConfiguration = cfg
        lastKnownConfiguration = cfg
        preferences.put(StringNonKey.InsulinConfiguration, cfg)
    }

    private fun configuration(): JsonObject = lock.withLock {
        val jsonArray = buildJsonArray {
            insulins.forEach {
                try {
                    add(it.toJsonObject())
                } catch (_: Exception) {
                    //
                }
            }
        }
        return buildJsonObject {
            put("insulin", jsonArray)
        }
    }

    // Normalizes into a new list and swaps it in (see [insulins]). Does not persist; bootstrap does that.
    private fun applyConfiguration(configuration: JsonObject): Unit = lock.withLock {
        val loaded = ArrayList<ICfg>()
        val insulinArray = configuration["insulin"] as? JsonArray
        insulinArray?.forEach { jsonElement ->
            try {
                val jsonObject = jsonElement as? JsonObject ?: return@forEach
                val newICfg = ICfg.fromJsonObject(jsonObject)
                if (newICfg.insulinNickname.isBlank()) {
                    val template = InsulinType.fromPeak(newICfg.insulinPeakTime)
                    newICfg.insulinNickname = rh.gs(template.label)
                }
                if (!containsEqual(loaded, newICfg, excludeIndex = -1)) // No Duplicated Insulin Allowed
                    addTo(loaded, newICfg, ue = newICfg.insulinLabel.isEmpty())
            } catch (_: Exception) {
                //
            }
        }
        // Also when every entry failed to parse, not only for an empty array: the list is never empty.
        if (loaded.isEmpty()) addTo(loaded, InsulinType.OREF_RAPID_ACTING.getICfg(rh), ue = true)
        insulins = loaded
    }

    fun deepClone(iCfg: ICfg, withoutName: Boolean = false): ICfg = iCfg.deepClone().also {
        if (withoutName)
            it.insulinLabel = ""
    }
}
