package app.aaps.pump.carelevo.data.dao

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.StringNonPreferenceKey
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.common.latest
import app.aaps.pump.carelevo.common.latestValueFlow
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Preference-backed store of the four per-mode infusion records (basal / temp-basal / immediate
 * bolus / extended bolus), each persisted as Gson under its own [CarelevoStringNonKey] and mirrored
 * into one aggregate [CarelevoInfusionInfoEntity] on the [_infusionInfo] stream.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CarelevoInfusionInfoDaoImpl @Inject constructor(
    private val preferences: Preferences,
) : CarelevoInfusionInfoDao {

    private val _infusionInfo: MutableSharedFlow<CarelevoInfusionInfoEntity?> = latestValueFlow()

    /** Load one per-mode record from its preference key; null when absent or unparseable. */
    private inline fun <reified T> loadEntity(key: StringNonPreferenceKey): T? = runCatching {
        val json = preferences.get(key)
        if (json == "") throw NullPointerException("${key.key} is empty")
        CarelevoGsonHelper.sharedGson().fromJson(json, T::class.java)
    }.fold(
        onSuccess = { it },
        onFailure = {
            it.printStackTrace()
            null
        }
    )

    /** Seed [_infusionInfo] from prefs if this is the first read of the process. */
    private fun ensureLoaded() {
        if (_infusionInfo.replayCache.isNotEmpty()) return
        val infusionInfo = CarelevoInfusionInfoEntity(
            basalInfusionInfo = loadEntity<CarelevoBasalInfusionInfoEntity>(CarelevoStringNonKey.BasalInfusionInfo),
            tempBasalInfusionInfo = loadEntity<CarelevoTempBasalInfusionInfoEntity>(CarelevoStringNonKey.TempBasalInfusionInfo),
            immeBolusInfusionInfo = loadEntity<CarelevoImmeBolusInfusionInfoEntity>(CarelevoStringNonKey.ImmeBolusInfusionInfo),
            extendBolusInfusionInfo = loadEntity<CarelevoExtendBolusInfusionInfoEntity>(CarelevoStringNonKey.ExtendBolusInfusionInfo)
        ).takeUnless { it.basalInfusionInfo == null && it.tempBasalInfusionInfo == null && it.immeBolusInfusionInfo == null && it.extendBolusInfusionInfo == null }
        _infusionInfo.tryEmit(infusionInfo)
    }

    override fun getInfusionInfo(): Flow<CarelevoInfusionInfoEntity?> {
        ensureLoaded()
        return _infusionInfo
    }

    override fun getInfusionInfoBySync(): CarelevoInfusionInfoEntity? {
        ensureLoaded()
        return _infusionInfo.latest
    }

    /**
     * Persist one per-mode record and fold it into the aggregate subject. [mutate] applies the new
     * record to the current aggregate (creating one if none exists yet).
     */
    private fun updateField(key: StringNonPreferenceKey, info: Any, mutate: (CarelevoInfusionInfoEntity) -> CarelevoInfusionInfoEntity): Boolean = runCatching {
        preferences.put(key, CarelevoGsonHelper.sharedGson().toJson(info))
    }.fold(
        onSuccess = {
            val current = _infusionInfo.latest ?: CarelevoInfusionInfoEntity()
            _infusionInfo.tryEmit(mutate(current))
            true
        },
        onFailure = {
            it.printStackTrace()
            false
        }
    )

    override fun updateBasalInfusionInfo(info: CarelevoBasalInfusionInfoEntity): Boolean =
        updateField(CarelevoStringNonKey.BasalInfusionInfo, info) { it.copy(basalInfusionInfo = info) }

    override fun updateTempBasalInfusionInfo(info: CarelevoTempBasalInfusionInfoEntity): Boolean =
        updateField(CarelevoStringNonKey.TempBasalInfusionInfo, info) { it.copy(tempBasalInfusionInfo = info) }

    override fun updateImmeBolusInfusionInfo(info: CarelevoImmeBolusInfusionInfoEntity): Boolean =
        updateField(CarelevoStringNonKey.ImmeBolusInfusionInfo, info) { it.copy(immeBolusInfusionInfo = info) }

    override fun updateExtendBolusInfusionInfo(info: CarelevoExtendBolusInfusionInfoEntity): Boolean =
        updateField(CarelevoStringNonKey.ExtendBolusInfusionInfo, info) { it.copy(extendBolusInfusionInfo = info) }

    /**
     * Remove one per-mode record and clear it from the aggregate; the aggregate collapses to
     * absent when its last record is removed.
     */
    private fun deleteField(key: StringNonPreferenceKey, mutate: (CarelevoInfusionInfoEntity) -> CarelevoInfusionInfoEntity): Boolean = runCatching {
        preferences.remove(key)
    }.fold(
        onSuccess = {
            val infusionInfo = _infusionInfo.latest?.let(mutate)
                ?.takeUnless { it.basalInfusionInfo == null && it.tempBasalInfusionInfo == null && it.immeBolusInfusionInfo == null && it.extendBolusInfusionInfo == null }
            _infusionInfo.tryEmit(infusionInfo)
            true
        },
        onFailure = {
            it.printStackTrace()
            false
        }
    )

    override fun deleteBasalInfusionInfo(): Boolean =
        deleteField(CarelevoStringNonKey.BasalInfusionInfo) { it.copy(basalInfusionInfo = null) }

    override fun deleteTempBasalInfusionInfo(): Boolean =
        deleteField(CarelevoStringNonKey.TempBasalInfusionInfo) { it.copy(tempBasalInfusionInfo = null) }

    override fun deleteImmeBolusInfusionInfo(): Boolean =
        deleteField(CarelevoStringNonKey.ImmeBolusInfusionInfo) { it.copy(immeBolusInfusionInfo = null) }

    override fun deleteExtendBolusInfusionInfo(): Boolean =
        deleteField(CarelevoStringNonKey.ExtendBolusInfusionInfo) { it.copy(extendBolusInfusionInfo = null) }

    override fun deleteInfusionInfo(): Boolean {
        return runCatching {
            preferences.remove(CarelevoStringNonKey.BasalInfusionInfo)
            preferences.remove(CarelevoStringNonKey.TempBasalInfusionInfo)
            preferences.remove(CarelevoStringNonKey.ImmeBolusInfusionInfo)
            preferences.remove(CarelevoStringNonKey.ExtendBolusInfusionInfo)
        }.fold(
            onSuccess = {
                _infusionInfo.tryEmit(null)
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }
}
