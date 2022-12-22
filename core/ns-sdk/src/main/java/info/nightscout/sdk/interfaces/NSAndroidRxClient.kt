package info.nightscout.sdk.interfaces

import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.sdk.remotemodel.RemoteDeviceStatus
import io.reactivex.rxjava3.core.Single

interface NSAndroidRxClient {

    fun getVersion(): Single<String>
    fun getStatus(): Single<Status>
    fun getLastModified(): Single<LastModified>
    fun getSgvsModifiedSince(from: Long, limit: Long): Single<NSAndroidClient.ReadResponse<List<NSSgvV3>>>
    fun getTreatmentsModifiedSince(from: Long, limit: Long): Single<NSAndroidClient.ReadResponse<List<NSTreatment>>>
    fun getDeviceStatusModifiedSince(from: Long): Single<List<RemoteDeviceStatus>>
}

