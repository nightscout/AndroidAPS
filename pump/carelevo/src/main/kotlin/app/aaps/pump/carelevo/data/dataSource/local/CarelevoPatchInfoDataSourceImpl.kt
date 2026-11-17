package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.dao.CarelevoPatchInfoDao
import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow

@ContributesBinding(AppScope::class)
class CarelevoPatchInfoDataSourceImpl @Inject constructor(
    private val patchInfoDao: CarelevoPatchInfoDao
) : CarelevoPatchInfoDataSource {

    override fun getPatchInfo(): Flow<CarelevoPatchInfoEntity?> {
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