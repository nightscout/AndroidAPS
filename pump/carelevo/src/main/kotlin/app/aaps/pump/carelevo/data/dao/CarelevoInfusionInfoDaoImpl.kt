package app.aaps.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.config.PrefEnvConfig
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

/**
 * SharedPreferences-backed store of the four per-mode infusion records (basal / temp-basal /
 * immediate bolus / extended bolus), each persisted as Gson under its own key and mirrored into
 * one aggregate [CarelevoInfusionInfoEntity] on the [_infusionInfo] BehaviorSubject.
 */
class CarelevoInfusionInfoDaoImpl @Inject constructor(
    private val prefManager: SP,
) : CarelevoInfusionInfoDao {

    private val _infusionInfo: BehaviorSubject<Optional<CarelevoInfusionInfoEntity>> = BehaviorSubject.create()

    /** Load one per-mode record from its preference key; null when absent or unparseable. */
    private inline fun <reified T> loadEntity(key: String): T? = runCatching {
        val json = prefManager.getString(key, "")
        if (json == "") throw NullPointerException("$key is empty")
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
        if (_infusionInfo.value != null) return
        val infusionInfo = CarelevoInfusionInfoEntity(
            basalInfusionInfo = loadEntity<CarelevoBasalInfusionInfoEntity>(PrefEnvConfig.BASAL_INFUSION_INFO),
            tempBasalInfusionInfo = loadEntity<CarelevoTempBasalInfusionInfoEntity>(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO),
            immeBolusInfusionInfo = loadEntity<CarelevoImmeBolusInfusionInfoEntity>(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO),
            extendBolusInfusionInfo = loadEntity<CarelevoExtendBolusInfusionInfoEntity>(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO)
        ).takeUnless { it.basalInfusionInfo == null && it.tempBasalInfusionInfo == null && it.immeBolusInfusionInfo == null && it.extendBolusInfusionInfo == null }
        _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
    }

    override fun getInfusionInfo(): Observable<Optional<CarelevoInfusionInfoEntity>> {
        ensureLoaded()
        return _infusionInfo
    }

    override fun getInfusionInfoBySync(): CarelevoInfusionInfoEntity? {
        ensureLoaded()
        return _infusionInfo.value?.getOrNull()
    }

    /**
     * Persist one per-mode record and fold it into the aggregate subject. [mutate] applies the new
     * record to the current aggregate (creating one if none exists yet).
     */
    private fun updateField(key: String, info: Any, mutate: (CarelevoInfusionInfoEntity) -> CarelevoInfusionInfoEntity): Boolean = runCatching {
        prefManager.putString(key, CarelevoGsonHelper.sharedGson().toJson(info))
    }.fold(
        onSuccess = {
            val current = _infusionInfo.value?.getOrNull() ?: CarelevoInfusionInfoEntity()
            _infusionInfo.onNext(Optional.of(mutate(current)))
            true
        },
        onFailure = {
            it.printStackTrace()
            false
        }
    )

    override fun updateBasalInfusionInfo(info: CarelevoBasalInfusionInfoEntity): Boolean =
        updateField(PrefEnvConfig.BASAL_INFUSION_INFO, info) { it.copy(basalInfusionInfo = info) }

    override fun updateTempBasalInfusionInfo(info: CarelevoTempBasalInfusionInfoEntity): Boolean =
        updateField(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO, info) { it.copy(tempBasalInfusionInfo = info) }

    override fun updateImmeBolusInfusionInfo(info: CarelevoImmeBolusInfusionInfoEntity): Boolean =
        updateField(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO, info) { it.copy(immeBolusInfusionInfo = info) }

    override fun updateExtendBolusInfusionInfo(info: CarelevoExtendBolusInfusionInfoEntity): Boolean =
        updateField(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO, info) { it.copy(extendBolusInfusionInfo = info) }

    /**
     * Remove one per-mode record and clear it from the aggregate; the aggregate collapses to
     * absent when its last record is removed.
     */
    private fun deleteField(key: String, mutate: (CarelevoInfusionInfoEntity) -> CarelevoInfusionInfoEntity): Boolean = runCatching {
        prefManager.remove(key)
    }.fold(
        onSuccess = {
            val infusionInfo = _infusionInfo.value?.getOrNull()?.let(mutate)
                ?.takeUnless { it.basalInfusionInfo == null && it.tempBasalInfusionInfo == null && it.immeBolusInfusionInfo == null && it.extendBolusInfusionInfo == null }
            _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
            true
        },
        onFailure = {
            it.printStackTrace()
            false
        }
    )

    override fun deleteBasalInfusionInfo(): Boolean =
        deleteField(PrefEnvConfig.BASAL_INFUSION_INFO) { it.copy(basalInfusionInfo = null) }

    override fun deleteTempBasalInfusionInfo(): Boolean =
        deleteField(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO) { it.copy(tempBasalInfusionInfo = null) }

    override fun deleteImmeBolusInfusionInfo(): Boolean =
        deleteField(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO) { it.copy(immeBolusInfusionInfo = null) }

    override fun deleteExtendBolusInfusionInfo(): Boolean =
        deleteField(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO) { it.copy(extendBolusInfusionInfo = null) }

    override fun deleteInfusionInfo(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.BASAL_INFUSION_INFO)
            prefManager.remove(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO)
            prefManager.remove(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO)
            prefManager.remove(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO)
        }.fold(
            onSuccess = {
                _infusionInfo.onNext(Optional.ofNullable(null))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }
}
