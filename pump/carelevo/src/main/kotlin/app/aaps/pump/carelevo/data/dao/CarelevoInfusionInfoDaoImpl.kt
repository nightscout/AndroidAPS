package app.aaps.pump.carelevo.data.dao

import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.pump.carelevo.config.PrefEnvConfig
import app.aaps.pump.carelevo.data.common.CarelevoGsonHelper
import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoInfusionInfoDaoImpl @Inject constructor(
    private val prefManager: SP,
) : CarelevoInfusionInfoDao {

    private val _infusionInfo: BehaviorSubject<Optional<CarelevoInfusionInfoEntity>> = BehaviorSubject.create()

    override fun getInfusionInfo(): Observable<Optional<CarelevoInfusionInfoEntity>> {
        if (_infusionInfo.value == null) {
            val basalInfusionInfo = runCatching {
                val basalInfoString = prefManager.getString(PrefEnvConfig.BASAL_INFUSION_INFO, "")
                if (basalInfoString == "") {
                    throw NullPointerException("basal infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(basalInfoString, CarelevoBasalInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val tempBasalInfusionInfo = runCatching {
                val tempBasalInfoString = prefManager.getString(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO, "")
                if (tempBasalInfoString == "") {
                    throw NullPointerException("temp basal infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(tempBasalInfoString, CarelevoTempBasalInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val immeBolusInfusionInfo = runCatching {
                val immeBolusInfoString = prefManager.getString(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO, "")
                if (immeBolusInfoString == "") {
                    throw NullPointerException("imme bolus infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(immeBolusInfoString, CarelevoImmeBolusInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val extendBolusInfusionInfo = runCatching {
                val extendBolusInfoString = prefManager.getString(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO, "")
                if (extendBolusInfoString == "") {
                    throw NullPointerException("extend bolus infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(extendBolusInfoString, CarelevoExtendBolusInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val infusionInfo = if (basalInfusionInfo == null && tempBasalInfusionInfo == null && immeBolusInfusionInfo == null && extendBolusInfusionInfo == null) {
                null
            } else {
                CarelevoInfusionInfoEntity(
                    basalInfusionInfo = basalInfusionInfo,
                    tempBasalInfusionInfo = tempBasalInfusionInfo,
                    immeBolusInfusionInfo = immeBolusInfusionInfo,
                    extendBolusInfusionInfo = extendBolusInfusionInfo
                )
            }
            _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
        }

        return _infusionInfo
    }

    override fun getInfusionInfoBySync(): CarelevoInfusionInfoEntity? {
        if (_infusionInfo.value == null) {
            val basalInfusionInfo = runCatching {
                val basalInfoString = prefManager.getString(PrefEnvConfig.BASAL_INFUSION_INFO, "")
                if (basalInfoString == "") {
                    throw NullPointerException("basal infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(basalInfoString, CarelevoBasalInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val tempBasalInfusionInfo = runCatching {
                val tempBasalInfoString = prefManager.getString(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO, "")
                if (tempBasalInfoString == "") {
                    throw NullPointerException("temp basal infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(tempBasalInfoString, CarelevoTempBasalInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val immeBolusInfusionInfo = runCatching {
                val immeBolusInfoString = prefManager.getString(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO, "")
                if (immeBolusInfoString == "") {
                    throw NullPointerException("imme bolus infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(immeBolusInfoString, CarelevoImmeBolusInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val extendBolusInfusionInfo = runCatching {
                val extendBolusInfoString = prefManager.getString(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO, "")
                if (extendBolusInfoString == "") {
                    throw NullPointerException("extend bolus infusion info is empty")
                }
                CarelevoGsonHelper.sharedGson().fromJson(extendBolusInfoString, CarelevoExtendBolusInfusionInfoEntity::class.java)
            }.fold(
                onSuccess = {
                    it
                },
                onFailure = {
                    it.printStackTrace()
                    null
                }
            )

            val infusionInfo = if (basalInfusionInfo == null && tempBasalInfusionInfo == null && immeBolusInfusionInfo == null && extendBolusInfusionInfo == null) {
                null
            } else {
                CarelevoInfusionInfoEntity(
                    basalInfusionInfo = basalInfusionInfo,
                    tempBasalInfusionInfo = tempBasalInfusionInfo,
                    immeBolusInfusionInfo = immeBolusInfusionInfo,
                    extendBolusInfusionInfo = extendBolusInfusionInfo
                )
            }

            _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
        }

        return _infusionInfo.value?.getOrNull()
    }

    override fun getBasalInfusionInfo(): CarelevoBasalInfusionInfoEntity? {
        return runCatching {
            val basalInfoString = prefManager.getString(PrefEnvConfig.BASAL_INFUSION_INFO, "")
            if (basalInfoString == "") {
                throw NullPointerException("basal infusion info is empty")
            }
            CarelevoGsonHelper.sharedGson().fromJson(basalInfoString, CarelevoBasalInfusionInfoEntity::class.java)
        }.fold(
            onSuccess = {
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(basalInfusionInfo = it)))
                it
            },
            onFailure = {
                it.printStackTrace()
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(basalInfusionInfo = null)))
                null
            }
        )
    }

    override fun getTempBasalInfusionInfo(): CarelevoTempBasalInfusionInfoEntity? {
        return runCatching {
            val tempBasalInfoString = prefManager.getString(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO, "")
            if (tempBasalInfoString == "") {
                throw NullPointerException("temp basal infusion info is empty")
            }
            CarelevoGsonHelper.sharedGson().fromJson(tempBasalInfoString, CarelevoTempBasalInfusionInfoEntity::class.java)
        }.fold(
            onSuccess = {
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(tempBasalInfusionInfo = it)))
                it
            },
            onFailure = {
                it.printStackTrace()
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(tempBasalInfusionInfo = null)))
                null
            }
        )
    }

    override fun getImmeBolusInfusionInfo(): CarelevoImmeBolusInfusionInfoEntity? {
        return runCatching {
            val immeBolusInfoString = prefManager.getString(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO, "")
            if (immeBolusInfoString == "") {
                throw NullPointerException("imme bolus infusion info is empty")
            }
            CarelevoGsonHelper.sharedGson().fromJson(immeBolusInfoString, CarelevoImmeBolusInfusionInfoEntity::class.java)
        }.fold(
            onSuccess = {
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(immeBolusInfusionInfo = it)))
                it
            },
            onFailure = {
                it.printStackTrace()
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(immeBolusInfusionInfo = null)))
                null
            }
        )
    }

    override fun getExtendBolusInfusionInfo(): CarelevoExtendBolusInfusionInfoEntity? {
        return runCatching {
            val extendBolusInfoString = prefManager.getString(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO, "")
            if (extendBolusInfoString == "") {
                throw NullPointerException("extend bolus infusion info is empty")
            }
            CarelevoGsonHelper.sharedGson().fromJson(extendBolusInfoString, CarelevoExtendBolusInfusionInfoEntity::class.java)
        }.fold(
            onSuccess = {
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(extendBolusInfusionInfo = it)))
                it
            },
            onFailure = {
                it.printStackTrace()
                _infusionInfo.onNext(Optional.ofNullable(_infusionInfo.value?.getOrNull()?.copy(extendBolusInfusionInfo = null)))
                null
            }
        )
    }

    override fun updateBasalInfusionInfo(info: CarelevoBasalInfusionInfoEntity): Boolean {
        return runCatching {
            val basalInfoString = CarelevoGsonHelper.sharedGson().toJson(info)
            prefManager.putString(PrefEnvConfig.BASAL_INFUSION_INFO, basalInfoString)
        }.fold(
            onSuccess = {
                val infusionInfo = if (_infusionInfo.value?.getOrNull() == null) {
                    CarelevoInfusionInfoEntity(
                        basalInfusionInfo = info
                    )
                } else {
                    _infusionInfo.value?.getOrNull()?.copy(basalInfusionInfo = info)
                }

                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun updateTempBasalInfusionInfo(info: CarelevoTempBasalInfusionInfoEntity): Boolean {
        return runCatching {
            val tempBasalInfoString = CarelevoGsonHelper.sharedGson().toJson(info)
            prefManager.putString(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO, tempBasalInfoString)
        }.fold(
            onSuccess = {
                val infusionInfo = if (_infusionInfo.value?.getOrNull() == null) {
                    CarelevoInfusionInfoEntity(
                        tempBasalInfusionInfo = info
                    )
                } else {
                    _infusionInfo.value?.getOrNull()?.copy(tempBasalInfusionInfo = info)
                }
                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun updateImmeBolusInfusionInfo(info: CarelevoImmeBolusInfusionInfoEntity): Boolean {
        return runCatching {
            val immeBolusInfoString = CarelevoGsonHelper.sharedGson().toJson(info)
            prefManager.putString(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO, immeBolusInfoString)
        }.fold(
            onSuccess = {
                val infusionInfo = if (_infusionInfo.value?.getOrNull() == null) {
                    CarelevoInfusionInfoEntity(
                        immeBolusInfusionInfo = info
                    )
                } else {
                    _infusionInfo.value?.getOrNull()?.copy(immeBolusInfusionInfo = info)
                }
                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun updateExtendBolusInfusionInfo(info: CarelevoExtendBolusInfusionInfoEntity): Boolean {
        return runCatching {
            val extendBolusInfoString = CarelevoGsonHelper.sharedGson().toJson(info)
            prefManager.putString(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO, extendBolusInfoString)
        }.fold(
            onSuccess = {
                val infusionInfo = if (_infusionInfo.value?.getOrNull() == null) {
                    CarelevoInfusionInfoEntity(
                        extendBolusInfusionInfo = info
                    )
                } else {
                    _infusionInfo.value?.getOrNull()?.copy(extendBolusInfusionInfo = info)
                }
                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun updateInfusionInfo(info: CarelevoInfusionInfoEntity): Boolean {
        return runCatching {
            val basalInfoString = CarelevoGsonHelper.sharedGson().toJson(info.basalInfusionInfo)
            prefManager.putString(PrefEnvConfig.BASAL_INFUSION_INFO, basalInfoString)

            val tempBasalInfoString = CarelevoGsonHelper.sharedGson().toJson(info.tempBasalInfusionInfo)
            prefManager.putString(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO, tempBasalInfoString)

            val immeBolusInfoString = CarelevoGsonHelper.sharedGson().toJson(info.immeBolusInfusionInfo)
            prefManager.putString(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO, immeBolusInfoString)

            val extendBolusInfoString = CarelevoGsonHelper.sharedGson().toJson(info.extendBolusInfusionInfo)
            prefManager.putString(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO, extendBolusInfoString)
        }.fold(
            onSuccess = {
                _infusionInfo.onNext(Optional.ofNullable(info))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun deleteBasalInfusionInfo(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.BASAL_INFUSION_INFO)
        }.fold(
            onSuccess = {
                val infusionInfo = _infusionInfo.value?.getOrNull()?.copy(
                    basalInfusionInfo = null
                )?.let {
                    if (it.tempBasalInfusionInfo == null && it.immeBolusInfusionInfo == null && it.extendBolusInfusionInfo == null) {
                        null
                    } else {
                        it
                    }
                }
                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun deleteTempBasalInfusionInfo(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO)
        }.fold(
            onSuccess = {
                val infusionInfo = _infusionInfo.value?.getOrNull()?.copy(
                    tempBasalInfusionInfo = null
                )?.let {
                    if (it.basalInfusionInfo == null && it.immeBolusInfusionInfo == null && it.extendBolusInfusionInfo == null) {
                        null
                    } else {
                        it
                    }
                }
                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun deleteImmeBolusInfusionInfo(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO)
        }.fold(
            onSuccess = {
                val infusionInfo = _infusionInfo.value?.getOrNull()?.copy(
                    immeBolusInfusionInfo = null
                )?.let {
                    if (it.basalInfusionInfo == null && it.tempBasalInfusionInfo == null && it.extendBolusInfusionInfo == null) {
                        null
                    } else {
                        it
                    }
                }
                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun deleteExtendBolusInfusionInfo(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO)
        }.fold(
            onSuccess = {
                val infusionInfo = _infusionInfo.value?.getOrNull()?.copy(
                    extendBolusInfusionInfo = null
                )?.let {
                    if (it.basalInfusionInfo == null && it.tempBasalInfusionInfo == null && it.immeBolusInfusionInfo == null) {
                        null
                    } else {
                        it
                    }
                }
                _infusionInfo.onNext(Optional.ofNullable(infusionInfo))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }

    override fun deleteInfusionInfo(): Boolean {
        return runCatching {
            prefManager.remove(PrefEnvConfig.BASAL_INFUSION_INFO)
            prefManager.remove(PrefEnvConfig.TEMP_BASAL_INFUSION_INFO)
            prefManager.remove(PrefEnvConfig.IMME_BOLUS_INFUSION_INFO)
            prefManager.remove(PrefEnvConfig.EXTEND_BOLUS_INFUSION_INFO)
        }.fold(
            onSuccess = {
                _infusionInfo.onNext(Optional.ofNullable(null))
                true
            },
            onFailure = {
                it.printStackTrace()
                false
            }
        )
    }
}
