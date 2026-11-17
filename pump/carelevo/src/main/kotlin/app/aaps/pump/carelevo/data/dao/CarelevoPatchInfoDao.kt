package app.aaps.pump.carelevo.data.dao

import app.aaps.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import kotlinx.coroutines.flow.Flow

interface CarelevoPatchInfoDao {

    fun getPatchInfo(): Flow<CarelevoPatchInfoEntity?>
    fun getPatchInfoBySync(): CarelevoPatchInfoEntity?

    fun updatePatchInfo(info: CarelevoPatchInfoEntity): Boolean
    fun deletePatchInfo(): Boolean
}