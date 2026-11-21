package app.aaps.pump.carelevo.data.dao

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.common.latest
import app.aaps.pump.carelevo.common.latestValueFlow
import app.aaps.pump.carelevo.data.common.carelevoJson
import app.aaps.pump.carelevo.data.common.readStoredJson
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CarelevoUserSettingInfoDaoImpl @Inject constructor(
    private val preferences: Preferences
) : CarelevoUserSettingInfoDao {

    private val _userSettingInfo: MutableSharedFlow<CarelevoUserSettingInfoEntity?> = latestValueFlow()

    override fun getUserSetting(): Flow<CarelevoUserSettingInfoEntity?> {
        if (_userSettingInfo.replayCache.isEmpty()) {
            _userSettingInfo.tryEmit(preferences.readStoredJson<CarelevoUserSettingInfoEntity>(CarelevoStringNonKey.UserSettingInfo))
        }

        return _userSettingInfo
    }

    override fun getUserSettingBySync(): CarelevoUserSettingInfoEntity? {
        if (_userSettingInfo.replayCache.isEmpty()) {
            _userSettingInfo.tryEmit(preferences.readStoredJson<CarelevoUserSettingInfoEntity>(CarelevoStringNonKey.UserSettingInfo))
        }
        return _userSettingInfo.latest
    }

    override fun updateUserSetting(setting: CarelevoUserSettingInfoEntity): Boolean {
        return runCatching {
            val userSettingInfoString = carelevoJson.encodeToString(setting)
            preferences.put(CarelevoStringNonKey.UserSettingInfo, userSettingInfoString)
        }.fold(
            onSuccess = {
                _userSettingInfo.tryEmit(setting)
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }
}