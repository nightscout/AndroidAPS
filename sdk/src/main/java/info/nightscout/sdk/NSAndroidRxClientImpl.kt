package info.nightscout.sdk

import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.interfaces.NSAndroidRxClient
import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.Sgv
import info.nightscout.sdk.localmodel.treatment.Treatment
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.rxSingle

class NSAndroidRxClientImpl(private val client: NSAndroidClient) : NSAndroidRxClient {

    override fun getVersion(): Single<String> = rxSingle { client.getVersion() }
    override fun getStatus(): Single<Status> = rxSingle { client.getStatus() }
    override fun getSgvsModifiedSince(from: Long): Single<List<Sgv>> = rxSingle { client.getSgvsModifiedSince(from) }
    override fun getTreatmentsModifiedSince(from: Long): Single<List<Treatment>> = rxSingle { client.getTreatmentsModifiedSince(from) }
}
