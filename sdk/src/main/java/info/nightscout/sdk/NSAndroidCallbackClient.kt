package info.nightscout.sdk

import android.content.Context
import info.nightscout.sdk.localmodel.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface NSAndroidCallbackClient {

    fun getStatus(callback: NSCallback<Status>): NSCancellable

    companion object { // TODO: test if callable from Java. If not, use named Object
        @JvmStatic
        @JvmOverloads
        fun create(
            baseUrl: String,
            accessToken: String,
            context: Context,
            logging: Boolean = false
        ): NSAndroidCallbackClient = NSAndroidCallbackClientImpl(
            NSAndroidClient(
                baseUrl = baseUrl,
                accessToken = accessToken,
                context = context,
                logging = logging
            )
        )
    }
}

private class NSAndroidCallbackClientImpl(private val client: NSAndroidClient) :
    NSAndroidCallbackClient {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Suppress("TooGenericExceptionCaught")
    override fun getStatus(callback: NSCallback<Status>): NSCancellable =
        NSJobCancellable(
            scope.launch {
                try {
                    callback.onSuccess(client.getStatus())
                } catch (e: Exception) {
                    callback.onFailure(e)
                }
            }
        )
}

interface NSCallback<T> {

    fun onSuccess(value: T)
    fun onFailure(exception: Exception)
}

interface NSCancellable {

    fun cancel()
}

private class NSJobCancellable(val job: Job) : NSCancellable {

    override fun cancel() = job.cancel()
}
