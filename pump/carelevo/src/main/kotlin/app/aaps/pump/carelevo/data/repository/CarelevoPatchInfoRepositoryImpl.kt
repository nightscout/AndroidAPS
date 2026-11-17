package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoPatchInfoEntity
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@ContributesBinding(AppScope::class)
class CarelevoPatchInfoRepositoryImpl @Inject constructor(
    private val patchInfoDataSource: CarelevoPatchInfoDataSource
) : CarelevoPatchInfoRepository {

    override fun getPatchInfo(): Flow<CarelevoPatchInfoDomainModel?> {
        return patchInfoDataSource.getPatchInfo()
            .map { it?.transformToCarelevoPatchInfoDomainModel() }
    }

    override fun getPatchInfoBySync(): CarelevoPatchInfoDomainModel? {
        return patchInfoDataSource.getPatchInfoBySync()?.transformToCarelevoPatchInfoDomainModel()
    }

    override fun updatePatchInfo(info: CarelevoPatchInfoDomainModel): Boolean {
        return patchInfoDataSource.updatePatchInfo(info.transformToCarelevoPatchInfoEntity())
    }

    override fun deletePatchInfo(): Boolean {
        return patchInfoDataSource.deletePatchInfo()
    }
}