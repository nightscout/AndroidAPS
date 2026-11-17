package app.aaps.pump.carelevo.domain.repository

import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import kotlinx.coroutines.flow.Flow

interface CarelevoUserSettingInfoRepository {

    fun getUserSettingInfo(): Flow<CarelevoUserSettingInfoDomainModel?>
    fun getUserSettingInfoBySync(): CarelevoUserSettingInfoDomainModel?

    fun updateUserSettingInfo(info: CarelevoUserSettingInfoDomainModel): Boolean
    fun deleteUserSettingInfo(): Boolean
}