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

// Metro builds this now; Dagger gets it through a @Provides delegate in `:app`. Scoped with Metro's
// @SingleIn, not javax @Singleton - the graph is generated in `:app`, which has no Dagger interop, so
// a javax scope there is ignored and every read would build a new one.
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class InsulinImpl @Inject constructor(
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
    // which these need - applyConfiguration() calls addNewInsulin(), and bootstrap() calls loadSettings().
    private val lock = AapsLock()

    // True while the one-time init normalization rebuilds the list via [applyConfiguration]; suppresses
    // the per-insulin [storeSettings] so the normalize persists at most once (via putRemote) at the end.
    @Volatile private var applying = false

    override var insulins: ArrayList<ICfg> = ArrayList()

    // Serialized config of the most recent LOCAL store. Stamped BEFORE preferences.put() at the single
    // store choke point so a UI observing InsulinConfiguration can recognize its own echo regardless of
    // dispatch timing (Main.immediate can deliver the change re-entrantly inside put()). @Volatile: written
    // under the @Synchronized store lock, read from the UI (main) thread without it.
    @Volatile override var lastStoredConfiguration: String = ""

    init {
        bootstrap()
        // Pick up master pushes: the cold-key bidirectional sync writes InsulinConfiguration via
        // putRemote. Client only — the master owns the canonical config and edits its own list directly.
        // Verbatim load → no re-store → no echo.
        if (config.AAPSCLIENT)
            preferences.observe(StringNonKey.InsulinConfiguration).drop(1).onEach { loadSettings() }.launchIn(appScope)
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
        val template = InsulinType.fromPeak(newICfg.insulinPeakTime)
        val nickname = newICfg.insulinNickname.ifBlank { rh.gs(template.label) }
        val fullName = buildFullName(
            nickname = nickname,
            peak = newICfg.peak,
            dia = newICfg.dia,
            concentration = newICfg.concentration,
            excludeIndex = -1
        )
        newICfg.insulinLabel = if (keepName) newICfg.insulinLabel.ifBlank { fullName } else fullName
        newICfg.insulinNickname = nickname
        val newInsulin = deepClone(newICfg)
        insulins.add(newInsulin)
        if (ue) {
            uel.log(Action.NEW_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(fullName))
        }

        storeSettings()
        newInsulin
    }

    override fun removeInsulin(index: Int) = lock.withLock {
        if (insulins.size <= 1) return@withLock // the catalogue is never emptied — the pickers must always have something to offer
        val insulinRemoved = insulins.getOrNull(index)?.insulinLabel ?: return@withLock
        insulins.removeAt(index)
        uel.log(Action.INSULIN_REMOVED, Sources.Insulin, value = ValueWithUnit.SimpleString(insulinRemoved))
        storeSettings()
    }

    override fun buildSuffix(peak: Int, dia: Double, concentration: Double): String {
        val concLabel = rh.gs(ConcentrationType.fromDouble(concentration).label)
        val diaLabel = if (dia % 1.0 == 0.0) "${dia.toInt()}h" else "${dia}h"
        return "${peak}m $diaLabel $concLabel"
    }

    override fun buildFullName(nickname: String, peak: Int, dia: Double, concentration: Double, excludeIndex: Int): String {
        val suffix = buildSuffix(peak, dia, concentration)
        val existingNames = insulins.mapIndexed { idx, it ->
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

    override fun insulinAlreadyExists(iCfg: ICfg, excludeIndex: Int): Boolean {
        insulins.forEachIndexed { index, insulin ->
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
    // happens once at init ([normalizeAndSeedOnce]) and at edit time ([addNewInsulin]); a master push is
    // already normalized, so applying it is a pure re-parse → no re-store → no echo loop.
    override fun loadSettings(): Unit = lock.withLock {
        insulins.clear()
        val insulinArray = runCatching {
            (Json.parseToJsonElement(preferences.get(StringNonKey.InsulinConfiguration)) as? JsonObject)?.get("insulin") as? JsonArray
        }.getOrNull()
        insulinArray?.forEach { element ->
            runCatching { (element as? JsonObject)?.let { insulins.add(ICfg.fromJsonObject(it)) } }
        }
    }

    // One-time at init.
    // CLIENT: mirror the master's config verbatim, seeding a normalized default ONLY when empty (so the
    // pickers always have something to offer). The master owns the canonical form, so client never
    // re-canonicalizes non-empty data — avoids cosmetically diverging from a master that serializes
    // slightly differently (mixed app versions).
    // MASTER: normalize legacy data (fill nicknames, dedup, regenerate labels, seed default) and persist
    // the canonical form once.
    // Either branch persists via putRemote: no client→master echo, stamp floored to the current value.
    private fun bootstrap(): Unit = lock.withLock {
        if (config.AAPSCLIENT) {
            loadSettings() // verbatim — master data stays untouched
            if (insulins.isEmpty()) {
                applying = true
                try {
                    addNewInsulin(InsulinType.OREF_RAPID_ACTING.getICfg(rh))
                } finally {
                    applying = false
                }
                persistBootstrap()
            }
        } else {
            val before = preferences.get(StringNonKey.InsulinConfiguration)
            val jsonObject = runCatching { Json.parseToJsonElement(before) as? JsonObject }.getOrNull()
            applying = true
            try {
                applyConfiguration(jsonObject ?: buildJsonObject {})
            } finally {
                applying = false
            }
            if (configuration().toString() != before) persistBootstrap()
        }
    }

    /** Persist the bootstrapped config via putRemote — no echo, stamp floored to the current value. */
    private fun persistBootstrap() {
        val cfg = configuration().toString()
        lastStoredConfiguration = cfg
        preferences.putRemote(
            StringNonKey.InsulinConfiguration, cfg,
            preferences.get(LongComposedKey.SyncedPrefModified, StringNonKey.InsulinConfiguration.key)
        )
    }

    override fun storeSettings(): Unit = lock.withLock {
        if (applying) return@withLock // the one-time init normalize persists once at the end via putRemote
        // Genuine edit → local put. The generic sync layer stamps SyncedPrefModified and signals the
        // client→master publisher on this write; no manual version bump needed.
        // Stamp lastStoredConfiguration BEFORE the put so an observer that receives the change
        // synchronously (Main.immediate re-entrancy inside put()) already sees the new value and can
        // suppress this self-echo.
        val cfg = configuration().toString()
        lastStoredConfiguration = cfg
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

    private fun applyConfiguration(configuration: JsonObject): Unit = lock.withLock {
        insulins.clear()

        val insulinArray = configuration["insulin"] as? JsonArray
        if (insulinArray.isNullOrEmpty()) {
            addNewInsulin(InsulinType.OREF_RAPID_ACTING.getICfg(rh))
            return@withLock
        }

        insulinArray.forEach { jsonElement ->
            try {
                val jsonObject = jsonElement as? JsonObject ?: return@forEach
                val newICfg = ICfg.fromJsonObject(jsonObject)
                if (newICfg.insulinNickname.isBlank()) {
                    val template = InsulinType.fromPeak(newICfg.insulinPeakTime)
                    newICfg.insulinNickname = rh.gs(template.label)
                }
                if (!insulinAlreadyExists(newICfg)) // No Duplicated Insulin Allowed
                    addNewInsulin(newICfg, newICfg.insulinLabel.isEmpty())
            } catch (_: Exception) {
                //
            }
        }
    }

    fun deepClone(iCfg: ICfg, withoutName: Boolean = false): ICfg = iCfg.deepClone().also {
        if (withoutName)
            it.insulinLabel = ""
    }
}
