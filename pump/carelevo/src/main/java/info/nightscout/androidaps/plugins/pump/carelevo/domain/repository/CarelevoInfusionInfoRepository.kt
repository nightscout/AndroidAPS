package info.nightscout.androidaps.plugins.pump.carelevo.domain.repository

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoInfusionInfoRepository {

    fun getInfusionInfo() : Observable<Optional<CarelevoInfusionInfoDomainModel>>
    fun getInfusionInfoBySync() : CarelevoInfusionInfoDomainModel?

    fun getBasalInfusionInfo() : CarelevoBasalInfusionInfoDomainModel?
    fun getTempBasalInfusionInfo() : CarelevoTempBasalInfusionInfoDomainModel?
    fun getImmeBolusInfusionInfo() : CarelevoImmeBolusInfusionInfoDomainModel?
    fun getExtendBolusInfusionInfo() : CarelevoExtendBolusInfusionInfoDomainModel?

    fun updateBasalInfusionInfo(info : CarelevoBasalInfusionInfoDomainModel) : Boolean
    fun updateTempBasalInfusionInfo(info : CarelevoTempBasalInfusionInfoDomainModel) : Boolean
    fun updateImmeBolusInfusionInfo(info : CarelevoImmeBolusInfusionInfoDomainModel) : Boolean
    fun updateExtendBolusInfusionInfo(info : CarelevoExtendBolusInfusionInfoDomainModel) : Boolean
    fun updateInfusionInfo(info : CarelevoInfusionInfoDomainModel) : Boolean

    fun deleteBasalInfusionInfo() : Boolean
    fun deleteTempBasalInfusionInfo() : Boolean
    fun deleteImmeBolusInfusionInfo() : Boolean
    fun deleteExtendBolusInfusionInfo() : Boolean
    fun deleteInfusionInfo() : Boolean
}