package info.nightscout.androidaps.plugins.pump.carelevo.domain.repository

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.userSetting.CarelevoUserSettingInfoDomainModel
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoUserSettingInfoRepository {

    fun getUserSettingInfo() : Observable<Optional<CarelevoUserSettingInfoDomainModel>>
    fun getUserSettingInfoBySync() : CarelevoUserSettingInfoDomainModel?

    fun updateUserSettingInfo(info : CarelevoUserSettingInfoDomainModel) : Boolean
    fun deleteUserSettingInfo() : Boolean
}