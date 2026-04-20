package info.nightscout.androidaps.plugins.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP
import info.nightscout.androidaps.plugins.pump.carelevo.config.PrefEnvConfig
import info.nightscout.androidaps.plugins.pump.carelevo.data.common.CarelevoGsonHelper
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoUserSettingInfoDaoImpl @Inject constructor(
    private val prefManager : SP
) : CarelevoUserSettingInfoDao {

    private val _userSettingInfo : BehaviorSubject<Optional<CarelevoUserSettingInfoEntity>> = BehaviorSubject.create()

    override fun getUserSetting(): Observable<Optional<CarelevoUserSettingInfoEntity>> {
        if(_userSettingInfo.value == null) {
            runCatching {
                val userSettingInfoString = prefManager.getString(PrefEnvConfig.USER_SETTING_INFO, "")
                if(userSettingInfoString == "") {
                    throw NullPointerException("user setting info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(userSettingInfoString, CarelevoUserSettingInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    _userSettingInfo.onNext(Optional.ofNullable(it))
                },
                onFailure = {
                    it.printStackTrace()
                    _userSettingInfo.onNext(Optional.ofNullable(null))
                }
            )
        }

        return _userSettingInfo
    }

    override fun getUserSettingBySync(): CarelevoUserSettingInfoEntity? {
        if(_userSettingInfo.value == null) {
            runCatching {
                val userSettingInfoString = prefManager.getString(PrefEnvConfig.USER_SETTING_INFO, "")
                if(userSettingInfoString == "") {
                    throw NullPointerException("user setting info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(userSettingInfoString, CarelevoUserSettingInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    _userSettingInfo.onNext(Optional.ofNullable(it))
                },
                onFailure = {
                    it.printStackTrace()
                    _userSettingInfo.onNext(Optional.ofNullable(null))
                }
            )
        }
        return _userSettingInfo.value?.getOrNull()
    }

    override fun updateUserSetting(setting: CarelevoUserSettingInfoEntity): Boolean {
        return runCatching {
            val userSettingInfoString = CarelevoGsonHelper.sharedGson().toJson(setting)
            prefManager.putString(PrefEnvConfig.USER_SETTING_INFO, userSettingInfoString)
        }.fold(
            onSuccess = {
                _userSettingInfo.onNext(Optional.ofNullable(setting))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun deleteUserSetting(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.USER_SETTING_INFO)
        }.fold(
            onSuccess = {
                _userSettingInfo.onNext(Optional.ofNullable(null))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }
}