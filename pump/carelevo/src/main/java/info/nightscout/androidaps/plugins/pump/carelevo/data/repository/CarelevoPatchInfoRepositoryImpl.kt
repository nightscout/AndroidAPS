package info.nightscout.androidaps.plugins.pump.carelevo.data.repository

import info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local.CarelevoPatchInfoDataSource
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.mapper.transformToCarelevoPatchInfoEntity
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import info.nightscout.androidaps.plugins.pump.carelevo.domain.repository.CarelevoPatchInfoRepository
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