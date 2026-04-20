package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local

import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoInfusionInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoBasalInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoExtendBolusInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoImmeBolusInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoInfusionInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoTempBasalInfusionInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional
import javax.inject.Inject

class CarelevoInfusionInfoDataSourceImpl @Inject constructor(
    private val infusionInfoDao : CarelevoInfusionInfoDao
) : CarelevoInfusionInfoDataSource {

    override fun getInfusionInfo(): Observable<Optional<CarelevoInfusionInfoEntity>> {
        return infusionInfoDao.getInfusionInfo()
    }

    override fun getInfusionInfoBySync(): CarelevoInfusionInfoEntity? {
        return infusionInfoDao.getInfusionInfoBySync()
    }

    override fun getBasalInfusionInfo(): CarelevoBasalInfusionInfoEntity? {
        return infusionInfoDao.getBasalInfusionInfo()
    }

    override fun getTemBasalInfusionInfo(): CarelevoTempBasalInfusionInfoEntity? {
        return infusionInfoDao.getTempBasalInfusionInfo()
    }

    override fun getImmeBolusInfusionInfo(): CarelevoImmeBolusInfusionInfoEntity? {
        return infusionInfoDao.getImmeBolusInfusionInfo()
    }

    override fun getExtendBolusInfusionInfo(): CarelevoExtendBolusInfusionInfoEntity? {
        return infusionInfoDao.getExtendBolusInfusionInfo()
    }

    override fun updateBasalInfusionInfo(info: CarelevoBasalInfusionInfoEntity): Boolean {
        return infusionInfoDao.updateBasalInfusionInfo(info)
    }

    override fun updateTempBasalInfusionInfo(info: CarelevoTempBasalInfusionInfoEntity): Boolean {
        return infusionInfoDao.updateTempBasalInfusionInfo(info)
    }

    override fun updateImmeBolusInfusionInfo(info: CarelevoImmeBolusInfusionInfoEntity): Boolean {
        return infusionInfoDao.updateImmeBolusInfusionInfo(info)
    }

    override fun updateExtendBolusInfusionInfo(info: CarelevoExtendBolusInfusionInfoEntity): Boolean {
        return infusionInfoDao.updateExtendBolusInfusionInfo(info)
    }

    override fun updateInfusionInfo(info: CarelevoInfusionInfoEntity): Boolean {
        return updateInfusionInfo(info)
    }

    override fun deleteBasalInfusionInfo(): Boolean {
        return infusionInfoDao.deleteBasalInfusionInfo()
    }

    override fun deleteTempBasalInfusionInfo(): Boolean {
        return infusionInfoDao.deleteTempBasalInfusionInfo()
    }

    override fun deleteImmeBolusInfusionInfo(): Boolean {
        return infusionInfoDao.deleteImmeBolusInfusionInfo()
    }

    override fun deleteExtendBolusInfusionInfo(): Boolean {
        return infusionInfoDao.deleteExtendBolusInfusionInfo()
    }

    override fun deleteInfusionInfo(): Boolean {
        return infusionInfoDao.deleteInfusionInfo()
    }
}