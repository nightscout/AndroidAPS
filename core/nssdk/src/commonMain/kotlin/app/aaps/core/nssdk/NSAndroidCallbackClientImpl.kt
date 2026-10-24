package app.aaps.core.nssdk

import app.aaps.core.nssdk.interfaces.NSAndroidCallbackClient
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.Status
import app.aaps.core.nssdk.utils.nsIoDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NSAndroidCallbackClientImpl(private val client: NSAndroidClient) :
    NSAndroidCallbackClient {

    private val scope = CoroutineScope(nsIoDispatcher + SupervisorJob())

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
