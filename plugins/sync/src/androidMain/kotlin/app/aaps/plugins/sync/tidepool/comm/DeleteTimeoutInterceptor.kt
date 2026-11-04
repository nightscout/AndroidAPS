package app.aaps.plugins.sync.tidepool.comm

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gives a dataset DELETE more time than the default 10 s read timeout. Tidepool answers a delete only
 * after it has removed all records, which takes longer than that for a big dataset, and the delete
 * does not finish when the client gives up - so purge failed again and again on the same dataset.
 * Other calls keep the short timeout, so a stuck upload does not hold the upload lock for minutes.
 */
class DeleteTimeoutInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response =
        if (chain.request().method == "DELETE") chain.withReadTimeout(DELETE_READ_TIMEOUT_MINUTES, TimeUnit.MINUTES).proceed(chain.request())
        else chain.proceed(chain.request())

    companion object {

        internal const val DELETE_READ_TIMEOUT_MINUTES = 2
    }
}
