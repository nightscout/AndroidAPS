package app.aaps.pump.carelevo.data.dao

import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoInfusionInfoDao {

    fun getInfusionInfo(): Observable<Optional<CarelevoInfusionInfoEntity>>
    fun getInfusionInfoBySync(): CarelevoInfusionInfoEntity?

    fun getBasalInfusionInfo(): CarelevoBasalInfusionInfoEntity?
    fun getTempBasalInfusionInfo(): CarelevoTempBasalInfusionInfoEntity?
    fun getImmeBolusInfusionInfo(): CarelevoImmeBolusInfusionInfoEntity?
    fun getExtendBolusInfusionInfo(): CarelevoExtendBolusInfusionInfoEntity?

    fun updateBasalInfusionInfo(info: CarelevoBasalInfusionInfoEntity): Boolean
    fun updateTempBasalInfusionInfo(info: CarelevoTempBasalInfusionInfoEntity): Boolean
    fun updateImmeBolusInfusionInfo(info: CarelevoImmeBolusInfusionInfoEntity): Boolean
    fun updateExtendBolusInfusionInfo(info: CarelevoExtendBolusInfusionInfoEntity): Boolean
    fun updateInfusionInfo(info: CarelevoInfusionInfoEntity): Boolean

    fun deleteBasalInfusionInfo(): Boolean
    fun deleteTempBasalInfusionInfo(): Boolean
    fun deleteImmeBolusInfusionInfo(): Boolean
    fun deleteExtendBolusInfusionInfo(): Boolean
    fun deleteInfusionInfo(): Boolean
}