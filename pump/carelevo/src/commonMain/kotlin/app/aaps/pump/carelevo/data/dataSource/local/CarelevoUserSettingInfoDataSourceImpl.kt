package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoUserSettingInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AppScope::class)
class CarelevoUserSettingInfoDataSourceImpl @Inject constructor(
    private val userSettingInfoDao: CarelevoUserSettingInfoDao
) : CarelevoUserSettingInfoDataSource {

    override fun getUserSettingInfo(): Flow<CarelevoUserSettingInfoEntity?> {
        return userSettingInfoDao.getUserSetting()
    }

    override fun getUserSettingInfoBySync(): CarelevoUserSettingInfoEntity? {
        return userSettingInfoDao.getUserSettingBySync()
    }

    override fun updateUserSettingInfo(info: CarelevoUserSettingInfoEntity): Boolean {
        return userSettingInfoDao.updateUserSetting(info)
    }
}