package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.local

import info.nightscout.androidaps.plugins.pump.carelevo.data.model.entities.CarelevoPatchInfoEntity
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoPatchInfoDataSource {

    fun getPatchInfo() : Observable<Optional<CarelevoPatchInfoEntity>>
    fun getPatchInfoBySync() : CarelevoPatchInfoEntity?

    fun updatePatchInfo(info : CarelevoPatchInfoEntity) : Boolean
    fun deletePatchInfo() : Boolean
}