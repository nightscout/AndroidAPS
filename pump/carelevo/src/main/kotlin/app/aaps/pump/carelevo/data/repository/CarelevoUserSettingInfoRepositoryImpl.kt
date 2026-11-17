package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSource
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoUserSettingInfoEntity
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class CarelevoUserSettingInfoRepositoryImpl @Inject constructor(
    private val userSettingInfoDataSource: CarelevoUserSettingInfoDataSource
) : CarelevoUserSettingInfoRepository {

    override fun getUserSettingInfo(): Flow<CarelevoUserSettingInfoDomainModel?> {
        return userSettingInfoDataSource.getUserSettingInfo()
            .map { it?.transformToCarelevoUserSettingInfoDomainModel() }
    }

    override fun getUserSettingInfoBySync(): CarelevoUserSettingInfoDomainModel? {
        return userSettingInfoDataSource.getUserSettingInfoBySync()?.transformToCarelevoUserSettingInfoDomainModel()
    }

    override fun updateUserSettingInfo(info: CarelevoUserSettingInfoDomainModel): Boolean {
        return userSettingInfoDataSource.updateUserSettingInfo(info.transformToCarelevoUserSettingInfoEntity())
    }
}