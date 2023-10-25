package app.aaps.core.nssdk

import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.interfaces.NSAndroidRxClient
import app.aaps.core.nssdk.localmodel.Status
import app.aaps.core.nssdk.remotemodel.LastModified
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.rx3.rxSingle

class NSAndroidRxClientImpl(private val client: NSAndroidClient) : NSAndroidRxClient {

    override fun getVersion(): Single<String> = rxSingle { client.getVersion() }
    override fun getStatus(): Single<Status> = rxSingle { client.getStatus() }
    override fun getLastModified(): Single<LastModified> = rxSingle { client.getLastModified() }
}
