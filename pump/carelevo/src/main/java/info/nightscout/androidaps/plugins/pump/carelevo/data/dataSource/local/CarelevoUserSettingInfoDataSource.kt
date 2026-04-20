package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoUserSettingInfoDataSource {

    fun getUserSettingInfo() : Observable<Optional<CarelevoUserSettingInfoEntity>>
    fun getUserSettingInfoBySync() : CarelevoUserSettingInfoEntity?

    fun updateUserSettingInfo(info : CarelevoUserSettingInfoEntity) : Boolean
    fun deleteUserSettingInfo() : Boolean
}