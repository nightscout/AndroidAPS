package app.aaps.pump.carelevo.data.dao

import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoUserSettingInfoDao {

    fun getUserSetting(): Observable<Optional<CarelevoUserSettingInfoEntity>>
    fun getUserSettingBySync(): CarelevoUserSettingInfoEntity?

    fun updateUserSetting(setting: CarelevoUserSettingInfoEntity): Boolean
    fun deleteUserSetting(): Boolean
}