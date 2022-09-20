package info.nightscout.sdk

import android.content.Context
import info.nightscout.sdk.localmodel.Status
import io.reactivex.Single
import kotlinx.coroutines.rx2.rxSingle

interface NSAndroidRxClient {

    fun getVersion(): Single<String>
    fun getStatus(): Single<Status>

    companion object { // TODO: test if callable from Java. If not, use named Object
        @JvmStatic
        @JvmOverloads
        fun create(
            baseUrl: String,
            accessToken: String,
            context: Context,
            logging: Boolean = false
        ): NSAndroidRxClient = NSAndroidRxClientImpl(
            NSAndroidClient(
                baseUrl = baseUrl,
                accessToken = accessToken,
                context = context,
                logging = logging
            )
        )
    }
}

private class NSAndroidRxClientImpl(private val client: NSAndroidClient) :
    NSAndroidRxClient {

    override fun getVersion(): Single<String> = rxSingle {
        client.getVersion()
    }

    override fun getStatus(): Single<Status> = rxSingle {
        client.getStatus()
    }
}
