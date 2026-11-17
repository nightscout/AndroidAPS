package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import kotlinx.coroutines.flow.Flow

interface CarelevoUserSettingInfoDataSource {

    fun getUserSettingInfo(): Flow<CarelevoUserSettingInfoEntity?>
    fun getUserSettingInfoBySync(): CarelevoUserSettingInfoEntity?

    fun updateUserSettingInfo(info: CarelevoUserSettingInfoEntity): Boolean
    fun deleteUserSettingInfo(): Boolean
}