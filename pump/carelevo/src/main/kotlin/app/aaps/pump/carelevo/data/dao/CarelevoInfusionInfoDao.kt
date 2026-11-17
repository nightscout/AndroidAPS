package app.aaps.pump.carelevo.data.dao

import app.aaps.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import app.aaps.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import kotlinx.coroutines.flow.Flow

interface CarelevoInfusionInfoDao {

    fun getInfusionInfo(): Flow<CarelevoInfusionInfoEntity?>
    fun getInfusionInfoBySync(): CarelevoInfusionInfoEntity?

    fun updateBasalInfusionInfo(info: CarelevoBasalInfusionInfoEntity): Boolean
    fun updateTempBasalInfusionInfo(info: CarelevoTempBasalInfusionInfoEntity): Boolean
    fun updateImmeBolusInfusionInfo(info: CarelevoImmeBolusInfusionInfoEntity): Boolean
    fun updateExtendBolusInfusionInfo(info: CarelevoExtendBolusInfusionInfoEntity): Boolean

    fun deleteBasalInfusionInfo(): Boolean
    fun deleteTempBasalInfusionInfo(): Boolean
    fun deleteImmeBolusInfusionInfo(): Boolean
    fun deleteExtendBolusInfusionInfo(): Boolean
    fun deleteInfusionInfo(): Boolean
}