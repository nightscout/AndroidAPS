package app.aaps.pump.carelevo.data.dao

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.pump.carelevo.common.keys.CarelevoStringNonKey
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoUserSettingInfoEntity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Optional
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.jvm.optionals.getOrNull

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class CarelevoUserSettingInfoDaoImpl @Inject constructor(
    private val preferences: Preferences
) : CarelevoUserSettingInfoDao {

    private val _userSettingInfo: BehaviorSubject<Optional<CarelevoUserSettingInfoEntity>> = BehaviorSubject.create()

    override fun getUserSetting(): Observable<Optional<CarelevoUserSettingInfoEntity>> {
        if (_userSettingInfo.value == null) {
            runCatching {
                val userSettingInfoString = preferences.get(CarelevoStringNonKey.UserSettingInfo)
                if (userSettingInfoString == "") {
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
        if (_userSettingInfo.value == null) {
            runCatching {
                val userSettingInfoString = preferences.get(CarelevoStringNonKey.UserSettingInfo)
                if (userSettingInfoString == "") {
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
            preferences.put(CarelevoStringNonKey.UserSettingInfo, userSettingInfoString)
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
            preferences.remove(CarelevoStringNonKey.UserSettingInfo)
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