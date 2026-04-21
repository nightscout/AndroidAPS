package info.nightscout.androidaps.plugins.pump.carelevo.data.repository

import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoInfusionInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoBasalInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoExtendBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoExtendBolusInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoImmeBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoImmeBolusInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoTempBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoTempBasalInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoExtendBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoImmeBolusInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.infusion.CarelevoTempBasalInfusionInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoInfusionInfoRepository
import io.reactivex.rxjava3.core.Observable
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoInfusionInfoRepositoryImpl @Inject constructor(
    private val infusionInfoDataSource : CarelevoInfusionInfoDataSource
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

    override fun getBasalInfusionInfo(): CarelevoBasalInfusionInfoDomainModel? {
        return infusionInfoDataSource.getBasalInfusionInfo()?.transformToCarelevoBasalInfusionInfoDomainModel()
    }

    override fun getTempBasalInfusionInfo(): CarelevoTempBasalInfusionInfoDomainModel? {
        return infusionInfoDataSource.getTemBasalInfusionInfo()?.transformToCarelevoTempBasalInfusionInfoDomainModel()
    }

    override fun getImmeBolusInfusionInfo(): CarelevoImmeBolusInfusionInfoDomainModel? {
        return infusionInfoDataSource.getImmeBolusInfusionInfo()?.transformToCarelevoImmeBolusInfusionInfoDomainModel()
    }

    override fun getExtendBolusInfusionInfo(): CarelevoExtendBolusInfusionInfoDomainModel? {
        return infusionInfoDataSource.getExtendBolusInfusionInfo()?.transformToCarelevoExtendBolusInfusionInfoDomainModel()
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

    override fun updateInfusionInfo(info: CarelevoInfusionInfoDomainModel): Boolean {
        return infusionInfoDataSource.updateInfusionInfo(info.transformToCarelevoInfusionInfoEntity())
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