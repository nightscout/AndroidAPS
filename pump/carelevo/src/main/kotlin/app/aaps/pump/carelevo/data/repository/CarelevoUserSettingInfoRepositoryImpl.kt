package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoUserSettingInfoDataSource
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoUserSettingInfoEntity
import app.aaps.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoUserSettingInfoRepository
import io.reactivex.rxjava3.core.Observable
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoUserSettingInfoRepositoryImpl @Inject constructor(
    private val userSettingInfoDataSource: CarelevoUserSettingInfoDataSource
) : CarelevoUserSettingInfoRepository {

    override fun getUserSettingInfo(): Observable<Optional<CarelevoUserSettingInfoDomainModel>> {
        return userSettingInfoDataSource.getUserSettingInfo()
            .map { Optional.ofNullable(it.getOrNull()?.transformToCarelevoUserSettingInfoDomainModel()) }
    }

    override fun getUserSettingInfoBySync(): CarelevoUserSettingInfoDomainModel? {
        return userSettingInfoDataSource.getUserSettingInfoBySync()?.transformToCarelevoUserSettingInfoDomainModel()
    }

    override fun updateUserSettingInfo(info: CarelevoUserSettingInfoDomainModel): Boolean {
        return userSettingInfoDataSource.updateUserSettingInfo(info.transformToCarelevoUserSettingInfoEntity())
    }

    override fun deleteUserSettingInfo(): Boolean {
        return userSettingInfoDataSource.deleteUserSettingInfo()
    }
}