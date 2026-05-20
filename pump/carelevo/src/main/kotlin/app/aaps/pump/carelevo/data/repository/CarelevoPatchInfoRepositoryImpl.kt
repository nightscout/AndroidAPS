package app.aaps.pump.carelevo.data.repository

import app.aaps.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.data.mapper.transformToCarelevoPatchInfoEntity
import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import app.aaps.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
import io.reactivex.rxjava3.core.Observable
import java.util.Optional
import javax.inject.Inject
import kotlin.jvm.optionals.getOrNull

class CarelevoPatchInfoRepositoryImpl @Inject constructor(
    private val patchInfoDataSource: CarelevoPatchInfoDataSource
) : CarelevoPatchInfoRepository {

    override fun getPatchInfo(): Observable<Optional<CarelevoPatchInfoDomainModel>> {
        return patchInfoDataSource.getPatchInfo()
            .map { Optional.ofNullable(it.getOrNull()?.transformToCarelevoPatchInfoDomainModel()) }
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