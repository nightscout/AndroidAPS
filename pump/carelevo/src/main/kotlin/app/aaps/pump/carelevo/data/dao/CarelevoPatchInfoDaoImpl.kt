package app.aaps.pump.carelevo.data.dao

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Optional
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.jvm.optionals.getOrNull

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CarelevoPatchInfoDaoImpl @Inject constructor(
    private val preferences: Preferences
) : CarelevoPatchInfoDao {

    private val _patchInfo: BehaviorSubject<Optional<CarelevoPatchInfoEntity>> = BehaviorSubject.create()

    override fun getPatchInfo(): Observable<Optional<CarelevoPatchInfoEntity>> {
        if (_patchInfo.value == null) {
            runCatching {
                val patchInfoString = preferences.get(CarelevoStringNonKey.PatchInfo)
                if (patchInfoString == "") {
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
        if (_patchInfo.value == null) {
            runCatching {
                val patchInfoString = preferences.get(CarelevoStringNonKey.PatchInfo)
                if (patchInfoString == "") {
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
            preferences.put(CarelevoStringNonKey.PatchInfo, patchInfoString)
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
            preferences.remove(CarelevoStringNonKey.PatchInfo)
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
