package app.aaps.pump.carelevo.data.dao

import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import kotlinx.coroutines.flow.Flow

interface CarelevoUserSettingInfoDao {

    fun getUserSetting(): Flow<CarelevoUserSettingInfoEntity?>
    fun getUserSettingBySync(): CarelevoUserSettingInfoEntity?

    fun updateUserSetting(setting: CarelevoUserSettingInfoEntity): Boolean
    fun deleteUserSetting(): Boolean
}