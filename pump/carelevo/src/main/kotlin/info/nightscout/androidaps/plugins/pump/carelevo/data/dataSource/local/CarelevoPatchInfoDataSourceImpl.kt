package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local

import info.nightscout.androidaps.plugins.pump.carelevo.data.dao.CarelevoPatchInfoDao
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional
import javax.inject.Inject

class CarelevoPatchInfoDataSourceImpl @Inject constructor(
    private val patchInfoDao : CarelevoPatchInfoDao
) : CarelevoPatchInfoDataSource {

    override fun getPatchInfo(): Observable<Optional<CarelevoPatchInfoEntity>> {
        return patchInfoDao.getPatchInfo()
    }

    override fun getPatchInfoBySync(): CarelevoPatchInfoEntity? {
        return patchInfoDao.getPatchInfoBySync()
    }

    override fun updatePatchInfo(info: CarelevoPatchInfoEntity): Boolean {
        return patchInfoDao.updatePatchInfo(info)
    }

    override fun deletePatchInfo(): Boolean {
        return patchInfoDao.deletePatchInfo()
    }
}