package app.aaps.pump.carelevo.domain.repository

import app.aaps.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import kotlinx.coroutines.flow.Flow

interface CarelevoPatchInfoRepository {

    fun getPatchInfo(): Flow<CarelevoPatchInfoDomainModel?>
    fun getPatchInfoBySync(): CarelevoPatchInfoDomainModel?

    fun updatePatchInfo(info: CarelevoPatchInfoDomainModel): Boolean
    fun deletePatchInfo(): Boolean
}