package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoUserSettingInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional
import javax.inject.Inject

class CarelevoUserSettingInfoDataSourceImpl @Inject constructor(
    private val userSettingInfoDao: CarelevoUserSettingInfoDao
) : CarelevoUserSettingInfoDataSource {

    override fun getUserSettingInfo(): Observable<Optional<CarelevoUserSettingInfoEntity>> {
        return userSettingInfoDao.getUserSetting()
    }

    override fun getUserSettingInfoBySync(): CarelevoUserSettingInfoEntity? {
        return userSettingInfoDao.getUserSettingBySync()
    }

    override fun updateUserSettingInfo(info: CarelevoUserSettingInfoEntity): Boolean {
        return userSettingInfoDao.updateUserSetting(info)
    }

    override fun deleteUserSettingInfo(): Boolean {
        return userSettingInfoDao.deleteUserSetting()
    }
}