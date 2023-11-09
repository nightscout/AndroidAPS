package app.aaps.core.nssdk.interfaces

import app.aaps.core.nssdk.localmodel.Status
import app.aaps.core.nssdk.remotemodel.LastModified
import io.reactivex.rxjava3.core.Single

interface NSAndroidRxClient {

    fun getVersion(): Single<String>
    fun getStatus(): Single<Status>
    fun getLastModified(): Single<LastModified>
}

