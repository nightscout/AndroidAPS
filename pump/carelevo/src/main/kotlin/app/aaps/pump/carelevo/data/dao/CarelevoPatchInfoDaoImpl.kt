package app.aaps.pump.carelevo.data.dao

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.common.latest
import app.aaps.pump.carelevo.common.latestValueFlow
import app.aaps.pump.carelevo.data.common.carelevoJson
import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CarelevoPatchInfoDaoImpl @Inject constructor(
    private val preferences: Preferences
) : CarelevoPatchInfoDao {

    private val _patchInfo: MutableSharedFlow<CarelevoPatchInfoEntity?> = latestValueFlow()

    override fun getPatchInfo(): Flow<CarelevoPatchInfoEntity?> {
        if (_patchInfo.replayCache.isEmpty()) {
            runCatching {
                val patchInfoString = preferences.get(CarelevoStringNonKey.PatchInfo)
                if (patchInfoString == "") {
                    throw NullPointerException("patch info is empty")
                }
                carelevoJson.decodeFromString<CarelevoPatchInfoEntity>(patchInfoString)
            }.fold(
                onSuccess = {
                    _patchInfo.tryEmit(it)
                },
                onFailure = {
                    it.printStackTrace()
                    _patchInfo.tryEmit(null)
                }
            )
        }
        return _patchInfo
    }

    override fun getPatchInfoBySync(): CarelevoPatchInfoEntity? {
        if (_patchInfo.replayCache.isEmpty()) {
            runCatching {
                val patchInfoString = preferences.get(CarelevoStringNonKey.PatchInfo)
                if (patchInfoString == "") {
                    throw NullPointerException("patch info is empty")
                }
                carelevoJson.decodeFromString<CarelevoPatchInfoEntity>(patchInfoString)
            }.fold(
                onSuccess = {
                    _patchInfo.tryEmit(it)
                },
                onFailure = {
                    it.printStackTrace()
                    _patchInfo.tryEmit(null)
                }
            )
        }

        return _patchInfo.latest
    }

    override fun updatePatchInfo(info: CarelevoPatchInfoEntity): Boolean {
        return runCatching {
            val patchInfoString = carelevoJson.encodeToString(info)
            preferences.put(CarelevoStringNonKey.PatchInfo, patchInfoString)
        }.fold(
            onSuccess = {
                _patchInfo.tryEmit(info)
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
                _patchInfo.tryEmit(null)
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }
}
