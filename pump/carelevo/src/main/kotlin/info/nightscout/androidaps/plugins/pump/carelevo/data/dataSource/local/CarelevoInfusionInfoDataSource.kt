package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoInfusionInfoDataSource {

    fun getInfusionInfo() : Observable<Optional<CarelevoInfusionInfoEntity>>
    fun getInfusionInfoBySync() : CarelevoInfusionInfoEntity?

    fun getBasalInfusionInfo() : CarelevoBasalInfusionInfoEntity?
    fun getTemBasalInfusionInfo() : CarelevoTempBasalInfusionInfoEntity?
    fun getImmeBolusInfusionInfo() : CarelevoImmeBolusInfusionInfoEntity?
    fun getExtendBolusInfusionInfo() : CarelevoExtendBolusInfusionInfoEntity?

    fun updateBasalInfusionInfo(info : CarelevoBasalInfusionInfoEntity) : Boolean
    fun updateTempBasalInfusionInfo(info : CarelevoTempBasalInfusionInfoEntity) : Boolean
    fun updateImmeBolusInfusionInfo(info : CarelevoImmeBolusInfusionInfoEntity) : Boolean
    fun updateExtendBolusInfusionInfo(info : CarelevoExtendBolusInfusionInfoEntity) : Boolean
    fun updateInfusionInfo(info : CarelevoInfusionInfoEntity) : Boolean

    fun deleteBasalInfusionInfo() : Boolean
    fun deleteTempBasalInfusionInfo() : Boolean
    fun deleteImmeBolusInfusionInfo() : Boolean
    fun deleteExtendBolusInfusionInfo() : Boolean
    fun deleteInfusionInfo() : Boolean
}