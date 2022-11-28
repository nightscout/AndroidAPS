package info.nightscout.sdk.interfaces

import info.nightscout.sdk.localmodel.Status
import kotlinx.coroutines.Job

interface NSAndroidCallbackClient {

    interface NSCallback<T> {

        fun onSuccess(value: T)
        fun onFailure(exception: Exception)
    }

    interface NSCancellable {

        fun cancel()
    }

    class NSJobCancellable(val job: Job) : NSCancellable {

        override fun cancel() = job.cancel()
    }

    fun getStatus(callback: NSCallback<Status>): NSCancellable
}

