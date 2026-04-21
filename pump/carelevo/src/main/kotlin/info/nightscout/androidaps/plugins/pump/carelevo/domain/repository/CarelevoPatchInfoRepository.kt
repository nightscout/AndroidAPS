package info.nightscout.androidaps.plugins.pump.carelevo.domain.repository

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.patch.CarelevoPatchInfoDomainModel
import io.reactivex.rxjava3.core.Observable
import java.util.Optional

interface CarelevoPatchInfoRepository {

    fun getPatchInfo() : Observable<Optional<CarelevoPatchInfoDomainModel>>
    fun getPatchInfoBySync() : CarelevoPatchInfoDomainModel?

    fun updatePatchInfo(info : CarelevoPatchInfoDomainModel) : Boolean
    fun deletePatchInfo() : Boolean
}