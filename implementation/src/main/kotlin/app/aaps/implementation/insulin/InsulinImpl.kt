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

    override val id = InsulinType.UNKNOWN
    override val friendlyName get(): String = rh.gs(app.aaps.core.interfaces.R.string.insulin_plugin)

    @Volatile private var cachedICfg: ICfg? = null

    override val iCfg: ICfg
        get() = cachedICfg ?: insulins[0]

    override val comment: String
        get() = TODO("Not yet implemented")

    lateinit var currentInsulin: ICfg

    override var insulins: ArrayList<ICfg> = ArrayList()
    override var currentInsulinIndex = 0

    init {
        loadSettings()
        persistenceLayer.observeChanges<EPS>()
            .onEach { updateCachedICfg() }
            .launchIn(appScope)
        appScope.launch { updateCachedICfg() }
    }

    private suspend fun updateCachedICfg() {
        try {
            cachedICfg = profileFunction.getProfile()?.iCfg
        } catch (_: Exception) {
            // May fail during early init (e.g. no APS selected yet).
            // Will be updated on first EPS change after init completes.
        }
    }

    override fun insulinList(concentration: Double?): List<CharSequence> {
        return insulins.filter {
            when {
                concentration == null -> it.concentration == iCfg.concentration
                concentration == 0.0  -> true
                concentration > 0.0   -> it.concentration == concentration
                else                  -> false
            }
        }.map {
            it.insulinLabel
        }
    }

    override fun getInsulin(insulinLabel: String): ICfg {
        insulins.forEach {
            if (it.insulinLabel == insulinLabel)
                return it
        }
        return iCfg     // if no insulin found then return current running iCfg
    }

    override fun getDefaultInsulin(concentration: Double?): ICfg {
        if (concentration == null || concentration == iCfg.concentration) return iCfg  // without null or current concentration, return current running iCfg
        return insulins.firstOrNull { it.concentration == concentration } ?: iCfg
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
    override fun addNewInsulin(newICfg: ICfg, ue: Boolean): ICfg {
        val template = InsulinType.fromPeak(newICfg.insulinPeakTime)
        val nickname = if (newICfg.insulinNickname.isNotBlank()) {
            newICfg.insulinNickname
        } else {
            rh.gs(template.label)
        }
        aapsLogger.debug("xxxxx addNewInsulin nickname $nickname ${newICfg.insulinNickname}")
        val fullName = buildFullName(
            nickname = nickname,
            peak = newICfg.peak,
            dia = newICfg.dia,
            concentration = newICfg.concentration,
            excludeIndex = -1
        )
        aapsLogger.debug("xxxxx addNewInsulin fullName $fullName")
        newICfg.insulinLabel = fullName
        newICfg.insulinNickname = nickname
        newICfg.insulinTemplate = template.value
        val newInsulin = deepClone(newICfg)
        insulins.add(newInsulin)
        currentInsulinIndex = insulins.size - 1
        if (ue) {
            uel.log(Action.NEW_INSULIN, Sources.Insulin, value = ValueWithUnit.SimpleString(fullName))
        }

        currentInsulin = deepClone(newInsulin)
        //currentInsulin.insulinTemplate = 0
        storeSettings()
        return newInsulin
    }

    @Synchronized
    override fun removeCurrentInsulin() {
        val insulinRemoved = currentInsulin().insulinLabel
        insulins.removeAt(currentInsulinIndex)
        uel.log(Action.INSULIN_REMOVED, Sources.Insulin, value = ValueWithUnit.SimpleString(insulinRemoved))
        currentInsulinIndex = 0     // Current running iCfg put in first position
        currentInsulin = deepClone(currentInsulin())
        storeSettings()
    }

    fun buildSuffix(peak: Int, dia: Double, concentration: Double): String {
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

    private fun insulinAlreadyExists(iCfg: ICfg, currentIndex: Int = -1): Boolean {
        insulins.forEachIndexed { index, insulin ->
            if (index != currentIndex) {
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
            addNewInsulin(InsulinType.OREF_RAPID_ACTING.getICfg(rh).also { it.insulinTemplate = InsulinType.OREF_RAPID_ACTING.ordinal })
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
