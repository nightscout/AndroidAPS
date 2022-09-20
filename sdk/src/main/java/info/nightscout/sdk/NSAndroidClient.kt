package info.nightscout.sdk

import android.content.Context
import info.nightscout.sdk.exceptions.DateHeaderOutOfToleranceException
import info.nightscout.sdk.exceptions.InvalidAccessTokenException
import info.nightscout.sdk.exceptions.TodoNightscoutException
import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.Sgv
import info.nightscout.sdk.mapper.toLocal
import info.nightscout.sdk.mapper.toSgv
import info.nightscout.sdk.networking.NetworkStackBuilder
import info.nightscout.sdk.remotemodel.RemoteEntry
import info.nightscout.sdk.utils.retry
import info.nightscout.sdk.utils.toNotNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface NSAndroidClient {

    suspend fun getVersion(): String
    suspend fun getStatus(): Status
    suspend fun getEntries(): String
    suspend fun getSgvs(): List<Sgv>
}

/**
 *
 * This client uses suspend functions and therefore is only visible in Kotlin (@JvmSynthetic).
 * An RxJava version can be found here [NSAndroidRxClient]
 *
 * @param baseUrl the baseURL of the NightScout Instance
 * @param accessToken the access token of a role found in the admin panel of the NightScout instance
 * @param logging if set to true, all network communication will be logged to logcat
 * @param dispatcher the coroutine dispatcher used for network calls.
 * Per default all network calls will be done on the IO thread pool. Change for Unit-Tests
 * @param context the application context.
 *
 * Todo: retry parameters (maxRetries, backoffFactor)?
 *
 * Todo: functions to modify baseUrl and accessToken?
 *  (not necessarily needed but might come handy if Client is provided by a DI framework like dagger)
 *
 * Todo: internal methods are still visible in Java bytecode -> tag @JvmSynthetic
 *
 * TODO: add message to Exceptions? wrap them?
 * */

@JvmSynthetic
@Suppress("FunctionNaming")
fun NSAndroidClient(
    baseUrl: String,
    accessToken: String,
    context: Context,
    logging: Boolean = false,
    dispatcher: CoroutineDispatcher = Dispatchers.IO
): NSAndroidClient =
    NSAndroidClientImpl(
        baseUrl = baseUrl,
        accessToken = accessToken,
        context = context.applicationContext, // applicationContext to leak activity context e.g.
        logging = logging,
        dispatcher = dispatcher
    )

private class NSAndroidClientImpl(
    baseUrl: String,
    accessToken: String,
    context: Context,
    logging: Boolean,
    private val dispatcher: CoroutineDispatcher
) : NSAndroidClient {

    val api = NetworkStackBuilder.getApi(
        baseUrl = baseUrl,
        context = context,
        accessToken = accessToken,
        logging = logging
    )

    /*
    * TODO: how should our result look like?
    *
    * Option A:
    * Directly hat the user asked for or an Exception. We can have our own Exceptions
    * -> re-throw to Exceptions with meaning
    * -> usually not that liked in Java with checked Exceptions and Rx
    *
    *
    *
    * Option B:
    * A Wrapper - sealed class that has success and error sub types.
    * Typical for Rx.
    *
    * */

    // TODO: we need a minimum NightscoutVersion for APIv3. Add to documentation
    override suspend fun getVersion(): String = callWrapper(dispatcher) {
        api.statusSimple().result!!.version
    }

    override suspend fun getStatus(): Status = callWrapper(dispatcher) {
        api.statusSimple().result!!.toLocal()
    }

    // TODO: return something better than a String
    // TODO: parameters like count?
    // TODO: updated after timestamp
    override suspend fun getEntries(): String = callWrapper(dispatcher) {
        api.getEntries().toString()
    }

    // TODO: parameters like count?
    override suspend fun getSgvs(): List<Sgv> = callWrapper(dispatcher) {

        val response = api.getSgvs()
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result?.map(RemoteEntry::toSgv).toNotNull()
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }
}

internal suspend fun <T> callWrapper(dispatcher: CoroutineDispatcher, block: suspend () -> T): T =
    withContext(dispatcher) {
        retry(
            numberOfRetries = RETRIES,
            delayBetweenRetries = RETRY_DELAY,
            excludedExceptions = listOf(
                InvalidAccessTokenException::class,
                DateHeaderOutOfToleranceException::class
            )
        ) {
            block.invoke()
        }
    }

// TODO: Parameters?
private const val RETRIES = 3
private const val RETRY_DELAY = 100L
