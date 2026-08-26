package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSource
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoBasalInfusionInfoEntity
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoExtendBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoImmeBolusInfusionInfoEntity
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoTempBasalInfusionInfoEntity
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import io.reactivex.rxjava3.core.Observable
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoInfusionInfoRepositoryImpl @Inject constructor(
    private val infusionInfoDataSource: CarelevoInfusionInfoDataSource
) : CarelevoInfusionInfoRepository {

    override fun getInfusionInfo(): Observable<Optional<CarelevoInfusionInfoDomainModel>> {
        return infusionInfoDataSource.getInfusionInfo()
            .map {
                Optional.ofNullable(it.getOrNull()?.transformToCarelevoInfusionInfoDomainModel())
            }
    }

    override fun getInfusionInfoBySync(): CarelevoInfusionInfoDomainModel? {
        return infusionInfoDataSource.getInfusionInfoBySync()?.transformToCarelevoInfusionInfoDomainModel()
    }

    override fun updateBasalInfusionInfo(info: CarelevoBasalInfusionInfoDomainModel): Boolean {
        return infusionInfoDataSource.updateBasalInfusionInfo(info.transformToCarelevoBasalInfusionInfoEntity())
    }

    override fun updateTempBasalInfusionInfo(info: CarelevoTempBasalInfusionInfoDomainModel): Boolean {
        return infusionInfoDataSource.updateTempBasalInfusionInfo(info.transformToCarelevoTempBasalInfusionInfoEntity())
    }

    override fun updateImmeBolusInfusionInfo(info: CarelevoImmeBolusInfusionInfoDomainModel): Boolean {
        return infusionInfoDataSource.updateImmeBolusInfusionInfo(info.transformToCarelevoImmeBolusInfusionInfoEntity())
    }

    override fun updateExtendBolusInfusionInfo(info: CarelevoExtendBolusInfusionInfoDomainModel): Boolean {
        return infusionInfoDataSource.updateExtendBolusInfusionInfo(info.transformToCarelevoExtendBolusInfusionInfoEntity())
    }

    override fun deleteBasalInfusionInfo(): Boolean {
        return infusionInfoDataSource.deleteBasalInfusionInfo()
    }

    override fun deleteTempBasalInfusionInfo(): Boolean {
        return infusionInfoDataSource.deleteTempBasalInfusionInfo()
    }

    override fun deleteImmeBolusInfusionInfo(): Boolean {
        return infusionInfoDataSource.deleteImmeBolusInfusionInfo()
    }

    override fun deleteExtendBolusInfusionInfo(): Boolean {
        return infusionInfoDataSource.deleteExtendBolusInfusionInfo()
    }

    override fun deleteInfusionInfo(): Boolean {
        return infusionInfoDataSource.deleteInfusionInfo()
    }
}