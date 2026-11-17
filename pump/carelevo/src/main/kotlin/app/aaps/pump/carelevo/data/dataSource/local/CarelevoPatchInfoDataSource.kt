package app.aaps.pump.carelevo.data.dataSource.local

import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import kotlinx.coroutines.flow.Flow

interface CarelevoPatchInfoDataSource {

    fun getPatchInfo(): Flow<CarelevoPatchInfoEntity?>
    fun getPatchInfoBySync(): CarelevoPatchInfoEntity?

    fun updatePatchInfo(info: CarelevoPatchInfoEntity): Boolean
    fun deletePatchInfo(): Boolean
}