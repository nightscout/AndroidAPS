package info.nightscout.sdk

import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.interfaces.NSAndroidRxClient
import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.sdk.remotemodel.RemoteDeviceStatus
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.rxSingle
import retrofit2.http.Query

class NSAndroidRxClientImpl(private val client: NSAndroidClient) : NSAndroidRxClient {

    override fun getVersion(): Single<String> = rxSingle { client.getVersion() }
    override fun getStatus(): Single<Status> = rxSingle { client.getStatus() }
    override fun getLastModified(): Single<LastModified> = rxSingle { client.getLastModified() }
    override fun getSgvsModifiedSince(from: Long, limit: Long): Single<NSAndroidClient.ReadResponse<List<NSSgvV3>>> = rxSingle { client.getSgvsModifiedSince(from, limit) }
    override fun getTreatmentsModifiedSince(from: Long, limit: Long): Single<NSAndroidClient.ReadResponse<List<NSTreatment>>> =
        rxSingle { client.getTreatmentsModifiedSince(from, limit) }
    override fun getDeviceStatusModifiedSince(from: Long): Single<List<RemoteDeviceStatus>> =
        rxSingle { client.getDeviceStatusModifiedSince(from) }
}
