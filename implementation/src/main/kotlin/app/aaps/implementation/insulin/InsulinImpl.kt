package app.aaps.implementation.insulin

import app.aaps.core.data.model.EPS
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.di.ApplicationScope
import app.aaps.core.interfaces.insulin.ConcentrationType
import app.aaps.core.interfaces.insulin.Insulin
import app.aaps.core.interfaces.insulin.InsulinManager
import app.aaps.core.interfaces.insulin.InsulinType
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.fromJsonObject
import app.aaps.core.objects.extensions.toJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Created by Philoul on 29.12.2024.
 */

@Singleton
class InsulinImpl @Inject constructor(
    private val preferences: Preferences,
    val rh: ResourceHelper,
    val profileFunction: ProfileFunction,
    val persistenceLayer: PersistenceLayer,
    val aapsLogger: AAPSLogger,
    val config: Config,
    val hardLimits: HardLimits,
    val uel: UserEntryLogger,
    @ApplicationScope private val appScope: CoroutineScope
) : Insulin, InsulinManager {

    // True while the one-time init normalization rebuilds the list via [applyConfiguration]; suppresses
    // the per-insulin [storeSettings] so the normalize persists at most once (via putRemote) at the end.
    @Volatile private var applying = false

    override val friendlyName get() = iCfg.insulinNickname  // No more used to delete or a way to provide Nickname ?

    @Volatile private var cachedICfg: ICfg? = null

    // Non-blocking: this getter is read during Compose composition (e.g. BolusCarbsScreen,
    // StatusViewModel), so it must never load the profile synchronously. The cache is populated
    // off-main in init and kept fresh by the EPS observer; until then fall back to the locally
    // stored running config (insulins[0], which holds the current insulin in first position).
    override val iCfg: ICfg
        get() = cachedICfg ?: insulins[0]

    override var insulins: ArrayList<ICfg> = ArrayList()
    override var currentInsulinIndex = 0

    init {
        bootstrap()
        // Populate the iCfg cache off the main thread so the synchronous getter never has to block.
        appScope.launch { updateCachedICfg() }
        persistenceLayer.observeChanges<EPS>()
            .collectResilient(appScope, aapsLogger, LTag.CORE) { updateCachedICfg() }
        // Pick up master pushes: the cold-key bidirectional sync writes InsulinConfiguration via
        // putRemote. Client only — the master owns the canonical config and edits its own list directly.
        // Verbatim load → no re-store → no echo.
        if (config.AAPSCLIENT)
            preferences.observe(StringNonKey.InsulinConfiguration).drop(1).onEach { loadSettings() }.launchIn(appScope)
    }

    private suspend fun updateCachedICfg() {
        cachedICfg = profileFunction.getProfile()?.iCfg
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

    @Synchronized
    override fun addNewInsulin(newICfg: ICfg, ue: Boolean, keepName: Boolean): ICfg {
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
        currentInsulinIndex = insulins.size - 1
        if (ue) {
            uel.log(Action.NEW_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(fullName))
        }

        storeSettings()
        return newInsulin
    }

    @Synchronized
    override fun removeCurrentInsulin() {
        if (insulins.size <= 1) return // invariant: the list keeps at least one insulin (iCfg falls back to insulins[0])
        val insulinRemoved = currentInsulin().insulinLabel
        insulins.removeAt(currentInsulinIndex)
        uel.log(Action.INSULIN_REMOVED, Sources.Insulin, value = ValueWithUnit.SimpleString(insulinRemoved))
        currentInsulinIndex = 0     // Current running iCfg put in first position
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
    @Synchronized
    override fun loadSettings() {
        insulins.clear()
        val insulinArray = runCatching {
            (Json.parseToJsonElement(preferences.get(StringNonKey.InsulinConfiguration)) as? JsonObject)?.get("insulin") as? JsonArray
        }.getOrNull()
        insulinArray?.forEach { element ->
            runCatching { (element as? JsonObject)?.let { insulins.add(ICfg.fromJsonObject(it)) } }
        }
        currentInsulinIndex = currentInsulinIndex.coerceIn(0, (insulins.size - 1).coerceAtLeast(0))
    }

    // One-time at init.
    // CLIENT: mirror the master's config verbatim, seeding a normalized default ONLY when empty (so
    // iCfg's insulins[0] fallback is safe). The master owns the canonical form, so client never
    // re-canonicalizes non-empty data — avoids cosmetically diverging from a master that serializes
    // slightly differently (mixed app versions).
    // MASTER: normalize legacy data (fill nicknames, dedup, regenerate labels, seed default) and persist
    // the canonical form once.
    // Either branch persists via putRemote: no client→master echo, stamp floored to the current value.
    @Synchronized
    private fun bootstrap() {
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
    private fun persistBootstrap() =
        preferences.putRemote(
            StringNonKey.InsulinConfiguration, configuration().toString(),
            preferences.get(LongComposedKey.SyncedPrefModified, StringNonKey.InsulinConfiguration.key)
        )

    @Synchronized
    override fun storeSettings() {
        if (applying) return // the one-time init normalize persists once at the end via putRemote
        // Genuine edit → local put. The generic sync layer stamps SyncedPrefModified and signals the
        // client→master publisher on this write; no manual version bump needed.
        preferences.put(StringNonKey.InsulinConfiguration, configuration().toString())
    }

    @Synchronized
    private fun configuration(): JsonObject {
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

    @Synchronized
    private fun applyConfiguration(configuration: JsonObject) {
        insulins.clear()

        val insulinArray = configuration["insulin"] as? JsonArray
        if (insulinArray.isNullOrEmpty()) {
            addNewInsulin(InsulinType.OREF_RAPID_ACTING.getICfg(rh))
            return
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

    fun currentInsulin(): ICfg = insulins[currentInsulinIndex]

    fun deepClone(iCfg: ICfg, withoutName: Boolean = false): ICfg = iCfg.deepClone().also {
        if (withoutName)
            it.insulinLabel = ""
    }
}
