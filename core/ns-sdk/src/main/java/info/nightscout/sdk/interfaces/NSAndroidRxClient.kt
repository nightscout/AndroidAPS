package info.nightscout.sdk.interfaces

import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.remotemodel.LastModified
import io.reactivex.rxjava3.core.Single

interface NSAndroidRxClient {

    fun getVersion(): Single<String>
    fun getStatus(): Single<Status>
    fun getLastModified(): Single<LastModified>
}

