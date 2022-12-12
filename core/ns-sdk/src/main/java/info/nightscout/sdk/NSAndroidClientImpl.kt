package info.nightscout.sdk

import android.content.Context
import info.nightscout.sdk.exceptions.DateHeaderOutOfToleranceException
import info.nightscout.sdk.exceptions.InvalidAccessTokenException
import info.nightscout.sdk.exceptions.InvalidFormatNightscoutException
import info.nightscout.sdk.exceptions.TodoNightscoutException
import info.nightscout.sdk.exceptions.UnknownResponseNightscoutException
import info.nightscout.sdk.interfaces.NSAndroidClient
import info.nightscout.sdk.localmodel.Status
import info.nightscout.sdk.localmodel.entry.NSSgvV3
import info.nightscout.sdk.localmodel.treatment.CreateUpdateResponse
import info.nightscout.sdk.localmodel.treatment.NSTreatment
import info.nightscout.sdk.mapper.toLocal
import info.nightscout.sdk.mapper.toRemoteTreatment
import info.nightscout.sdk.mapper.toSgv
import info.nightscout.sdk.mapper.toTreatment
import info.nightscout.sdk.networking.NetworkStackBuilder
import info.nightscout.sdk.remotemodel.LastModified
import info.nightscout.sdk.remotemodel.RemoteDeviceStatus
import info.nightscout.sdk.remotemodel.RemoteEntry
import info.nightscout.sdk.remotemodel.RemoteTreatment
import info.nightscout.sdk.utils.retry
import info.nightscout.sdk.utils.toNotNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 *
 * This client uses suspend functions and therefore is only visible in Kotlin (@JvmSynthetic).
 * An RxJava version can be found here [NSAndroidRxClientImpl]
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

class NSAndroidClientImpl(
    baseUrl: String,
    accessToken: String,
    context: Context,
    logging: Boolean,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : NSAndroidClient {

    internal val api = NetworkStackBuilder.getApi(
        baseUrl = baseUrl,
        context = context,
        accessToken = accessToken,
        logging = logging
    )
    override var lastStatus: Status? = null
        private set

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
        api.statusSimple().result!!.toLocal().also { lastStatus = it }
    }

    // TODO: return something better than a String
    // TODO: parameters like count?
    // TODO: updated after timestamp
    override suspend fun getEntries(): String = callWrapper(dispatcher) {
        api.getEntries().toString()
    }

    override suspend fun getLastModified(): LastModified = callWrapper(dispatcher) {

        val response = api.lastModified()
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result ?: throw TodoNightscoutException()
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    // TODO: parameters like count?
    override suspend fun getSgvs(): List<NSSgvV3> = callWrapper(dispatcher) {

        val response = api.getSgvs()
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result?.map(RemoteEntry::toSgv).toNotNull()
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    override suspend fun getSgvsModifiedSince(from: Long): List<NSSgvV3> = callWrapper(dispatcher) {

        val response = api.getSgvsModifiedSince(from)
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result?.map(RemoteEntry::toSgv).toNotNull()
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    override suspend fun getSgvsNewerThan(from: Long, limit: Long): List<NSSgvV3> = callWrapper(dispatcher) {

        val response = api.getSgvsNewerThan(from, limit)
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result?.map(RemoteEntry::toSgv).toNotNull()
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    override suspend fun getTreatmentsModifiedSince(from: Long, limit: Long): List<NSTreatment> = callWrapper(dispatcher) {

        val response = api.getTreatmentsModifiedSince(from, limit)
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result?.map(RemoteTreatment::toTreatment).toNotNull()
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    override suspend fun getDeviceStatusModifiedSince(from: Long): List<RemoteDeviceStatus> = callWrapper(dispatcher) {

        val response = api.getDeviceStatusModifiedSince(from)
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result.toNotNull()
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    override suspend fun createTreatment(nsTreatment: NSTreatment): CreateUpdateResponse = callWrapper(dispatcher) {

        val remoteTreatment = nsTreatment.toRemoteTreatment() ?: throw InvalidFormatNightscoutException()
        val response = api.createTreatment(remoteTreatment)
        if (response.isSuccessful) {
            return@callWrapper CreateUpdateResponse(
                identifier = response.body()?.result?.identifier ?: throw UnknownResponseNightscoutException(),
                isDeduplication = response.body()?.result?.isDeduplication ?: false,
                deduplicatedIdentifier = response.body()?.result?.deduplicatedIdentifier,
                lastModified = response.body()?.result?.lastModified
            )
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    override suspend fun updateTreatment(nsTreatment: NSTreatment): CreateUpdateResponse = callWrapper(dispatcher) {

        val remoteTreatment = nsTreatment.toRemoteTreatment() ?: throw InvalidFormatNightscoutException()
        val response = api.updateTreatment(remoteTreatment)
        if (response.isSuccessful) {
            return@callWrapper CreateUpdateResponse(
                identifier = response.body()?.result?.identifier ?: throw UnknownResponseNightscoutException(),
                isDeduplication = response.body()?.result?.isDeduplication ?: false,
                deduplicatedIdentifier = response.body()?.result?.deduplicatedIdentifier,
                lastModified = response.body()?.result?.lastModified
            )
        } else {
            throw TodoNightscoutException() // TODO: react to response errors (offline, ...)
        }
    }

    private suspend fun <T> callWrapper(dispatcher: CoroutineDispatcher, block: suspend () -> T): T =
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

    companion object {

        // TODO: Parameters?
        private const val RETRIES = 3
        private const val RETRY_DELAY = 100L
    }
}
