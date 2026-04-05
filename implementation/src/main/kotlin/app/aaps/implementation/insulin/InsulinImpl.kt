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
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.fromJsonObject
import app.aaps.core.objects.extensions.toJsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
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
    val preferences: Preferences,
    val rh: ResourceHelper,
    val profileFunction: ProfileFunction,
    val persistenceLayer: PersistenceLayer,
    val aapsLogger: AAPSLogger,
    val config: Config,
    val hardLimits: HardLimits,
    val uel: UserEntryLogger,
    @ApplicationScope private val appScope: CoroutineScope
) : Insulin, InsulinManager {

    override val id get() = InsulinType.fromPeak(iCfg.insulinPeakTime) // Only used within Autotune Plugin
    override val friendlyName get() = iCfg.insulinNickname  // No more used to delete or a way to provide Nickname ?

    @Volatile private var cachedICfg: ICfg? = null

    override val iCfg: ICfg
        get() = cachedICfg ?: run {
            cachedICfg = runBlocking { profileFunction.getProfile()?.iCfg }
            cachedICfg ?: insulins[0]
        }

    override var insulins: ArrayList<ICfg> = ArrayList()
    override var currentInsulinIndex = 0

    init {
        loadSettings()
        persistenceLayer.observeChanges<EPS>()
            .onEach { updateCachedICfg() }
            .launchIn(appScope)
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
        var full = "$nickname $suffix".trim()
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

    @Synchronized
    override fun loadSettings() {
        val jsonString = preferences.get(StringNonKey.InsulinConfiguration)
        val jsonObject = runCatching {
            Json.parseToJsonElement(jsonString) as? JsonObject
        }.getOrNull()
        applyConfiguration(jsonObject ?: buildJsonObject {})
    }

    @Synchronized
    override fun storeSettings() {
        preferences.put(StringNonKey.InsulinConfiguration, configuration().toString())
    }

    @Synchronized
    override fun configuration(): JsonObject {
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
    override fun applyConfiguration(configuration: JsonObject) {
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
