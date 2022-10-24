package info.nightscout.sdk

import info.nightscout.sdk.interfaces.NSAndroidCallbackClient
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.localmodel.Status
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NSAndroidCallbackClientImpl(private val client: NSAndroidClient) :
    NSAndroidCallbackClient {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Suppress("TooGenericExceptionCaught")
    override fun getStatus(callback: NSAndroidCallbackClient.NSCallback<Status>): NSAndroidCallbackClient.NSCancellable =
        NSAndroidCallbackClient.NSJobCancellable(
            scope.launch {
                try {
                    callback.onSuccess(client.getStatus())
                } catch (e: Exception) {
                    callback.onFailure(e)
                }
            }
        )
}
