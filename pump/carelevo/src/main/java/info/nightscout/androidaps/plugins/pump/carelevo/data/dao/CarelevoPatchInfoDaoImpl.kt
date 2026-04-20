package info.nightscout.androidaps.plugins.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP

import info.nightscout.androidaps.plugins.pump.carelevo.config.PrefEnvConfig
import info.nightscout.androidaps.plugins.pump.carelevo.data.common.CarelevoGsonHelper
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoPatchInfoDaoImpl @Inject constructor(
    private val prefManager : SP
) : CarelevoPatchInfoDao {

    private val _patchInfo : BehaviorSubject<Optional<CarelevoPatchInfoEntity>> = BehaviorSubject.create()

    override fun getPatchInfo(): Observable<Optional<CarelevoPatchInfoEntity>> {
        if(_patchInfo.value == null) {
            runCatching {
                val patchInfoString = prefManager.getString(PrefEnvConfig.PATCH_INFO, "")
                if(patchInfoString == "") {
                    throw NullPointerException("patch info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(patchInfoString, CarelevoPatchInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    _patchInfo.onNext(Optional.ofNullable(it))
                },
                onFailure = {
                    it.printStackTrace()
                    _patchInfo.onNext(Optional.ofNullable(null))
                }
            )
        }
        return _patchInfo
    }

    override fun getPatchInfoBySync(): CarelevoPatchInfoEntity? {
        if(_patchInfo.value == null) {
            runCatching {
                val patchInfoString = prefManager.getString(PrefEnvConfig.PATCH_INFO, "")
                if(patchInfoString == "") {
                    throw NullPointerException("patch info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(patchInfoString, CarelevoPatchInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    _patchInfo.onNext(Optional.ofNullable(it))
                },
                onFailure = {
                    it.printStackTrace()
                    _patchInfo.onNext(Optional.ofNullable(null))
                }
            )
        }

        return _patchInfo.value?.getOrNull()
    }

    override fun updatePatchInfo(info: CarelevoPatchInfoEntity): Boolean {
        return runCatching {
            val patchInfoString = CarelevoGsonHelper.sharedGson().toJson(info)
            prefManager.putString(PrefEnvConfig.PATCH_INFO, patchInfoString)
        }.fold(
            onSuccess = {
                _patchInfo.onNext(Optional.ofNullable(info))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun deletePatchInfo(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.PATCH_INFO)
        }.fold(
            onSuccess = {
                _patchInfo.onNext(Optional.ofNullable(null))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }
}