package app.aaps.core.nssdk

import android.content.Context
import com.google.gson.JsonParser
import app.aaps.core.nssdk.exceptions.DateHeaderOutOfToleranceException
import app.aaps.core.nssdk.exceptions.InvalidAccessTokenException
import app.aaps.core.nssdk.exceptions.InvalidFormatNightscoutException
import app.aaps.core.nssdk.exceptions.InvalidParameterNightscoutException
import app.aaps.core.nssdk.exceptions.UnknownResponseNightscoutException
import app.aaps.core.nssdk.exceptions.UnsuccessfullNightscoutException
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.localmodel.Status
import app.aaps.core.nssdk.localmodel.devicestatus.NSDeviceStatus
import app.aaps.core.nssdk.localmodel.entry.NSSgvV3
import app.aaps.core.nssdk.localmodel.food.NSFood
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.core.nssdk.localmodel.treatment.NSTreatment
import app.aaps.core.nssdk.mapper.toLocal
import app.aaps.core.nssdk.mapper.toNSDeviceStatus
import app.aaps.core.nssdk.mapper.toNSFood
import app.aaps.core.nssdk.mapper.toRemoteDeviceStatus
import app.aaps.core.nssdk.mapper.toRemoteEntry
import app.aaps.core.nssdk.mapper.toRemoteFood
import app.aaps.core.nssdk.mapper.toRemoteTreatment
import app.aaps.core.nssdk.mapper.toSgv
import app.aaps.core.nssdk.mapper.toTreatment
import app.aaps.core.nssdk.networking.NetworkStackBuilder
import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.nssdk.remotemodel.RemoteDeviceStatus
import app.aaps.core.nssdk.remotemodel.RemoteEntry
import app.aaps.core.nssdk.remotemodel.RemoteFood
import app.aaps.core.nssdk.remotemodel.RemoteTreatment
import app.aaps.core.nssdk.utils.retry
import app.aaps.core.nssdk.utils.toNotNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject

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
    logger: HttpLoggingInterceptor.Logger,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : NSAndroidClient {

    internal val api = NetworkStackBuilder.getApi(
        baseUrl = baseUrl,
        context = context,
        accessToken = accessToken,
        logging = logging,
        logger = logger
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

    override suspend fun getLastModified(): LastModified = callWrapper(dispatcher) {

        val response = api.lastModified()
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result ?: throw UnsuccessfullNightscoutException()
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun getSgvs(): NSAndroidClient.ReadResponse<List<NSSgvV3>> = callWrapper(dispatcher) {

        val response = api.getSgvs()
        if (response.isSuccessful) {
            return@callWrapper NSAndroidClient.ReadResponse(
                code = response.raw().networkResponse?.code ?: response.code(),
                lastServerModified = 0,
                values = response.body()?.result?.map(RemoteEntry::toSgv).toNotNull()
            )
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun getSgvsModifiedSince(from: Long, limit: Int): NSAndroidClient.ReadResponse<List<NSSgvV3>> = callWrapper(dispatcher) {

        val response = api.getSgvsModifiedSince(from, limit)
        if (response.isSuccessful) {
            val eTagString = response.headers()["ETag"]
            val eTag = eTagString?.substring(3, eTagString.length - 1)?.toLong()
            return@callWrapper NSAndroidClient.ReadResponse(
                code = response.raw().networkResponse?.code ?: response.code(),
                lastServerModified = eTag,
                values = response.body()?.result?.map(RemoteEntry::toSgv).toNotNull()
            )
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun getSgvsNewerThan(from: Long, limit: Int): NSAndroidClient.ReadResponse<List<NSSgvV3>> = callWrapper(dispatcher) {

        val response = api.getSgvsNewerThan(from, limit)
        if (response.isSuccessful) {
            return@callWrapper NSAndroidClient.ReadResponse(
                code = response.raw().networkResponse?.code ?: response.code(),
                lastServerModified = 0,
                values = response.body()?.result?.map(RemoteEntry::toSgv).toNotNull()
            )
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun createSgv(nsSgvV3: NSSgvV3): CreateUpdateResponse = callWrapper(dispatcher) {

        val remoteEntry = nsSgvV3.toRemoteEntry()
        remoteEntry.app = "AAPS"
        val response = api.createEntry(remoteEntry)
        val responseBody = response.body()
        val errorResponse = response.errorBody()?.string()
        if (response.code() == 200 || response.code() == 201) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = responseBody?.identifier,
                isDeduplication = responseBody?.isDeduplication ?: false,
                deduplicatedIdentifier = responseBody?.deduplicatedIdentifier,
                lastModified = responseBody?.lastModified
            )
        } else if (response.code() == 400 && errorResponse?.contains("Bad or missing utcOffset field") == true && nsSgvV3.utcOffset != 0L) {
            // Record can be originally uploaded without utcOffset
            // because utcOffset is mandatory and cannot be change, try 0
            nsSgvV3.utcOffset = 0
            return@callWrapper createSgv(nsSgvV3)
        } else if (response.code() == 400 && errorResponse?.contains("cannot be modified by the client") == true) {
            // there is different field to field in AAPS
            // not possible to upload
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = errorResponse
            )
        } else if (response.code() in 400..499) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = errorResponse ?: response.message()
            )
        } else
            throw UnsuccessfullNightscoutException(errorResponse ?: response.message())
    }

    override suspend fun updateSvg(nsSgvV3: NSSgvV3): CreateUpdateResponse = callWrapper(dispatcher) {

        // following cannot be updated
        nsSgvV3.utcOffset = null
        nsSgvV3.date = null
        val remoteEntry = nsSgvV3.toRemoteEntry()
        val identifier = remoteEntry.identifier ?: throw InvalidFormatNightscoutException()
        val response =
            if (nsSgvV3.isValid) api.updateEntry(remoteEntry, identifier)
            else api.deleteEntry(identifier)
        if (response.isSuccessful) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                isDeduplication = false,
                deduplicatedIdentifier = null,
                lastModified = null
            )
        } else if (response.code() == 404) { // not found
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                isDeduplication = false,
                deduplicatedIdentifier = null,
                lastModified = null
            )
        } else if (response.code() in 400..499) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = response.errorBody()?.string() ?: response.message()
            )
        } else
            throw UnsuccessfullNightscoutException(response.errorBody()?.string() ?: response.message())
    }

    override suspend fun getTreatmentsNewerThan(createdAt: String, limit: Int): NSAndroidClient.ReadResponse<List<NSTreatment>> = callWrapper(dispatcher) {

        val response = api.getTreatmentsNewerThan(createdAt, limit)
        if (response.isSuccessful) {
            return@callWrapper NSAndroidClient.ReadResponse(
                code = response.raw().networkResponse?.code ?: response.code(),
                lastServerModified = 0,
                values = response.body()?.result?.map(RemoteTreatment::toTreatment).toNotNull()
            )
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun getTreatmentsModifiedSince(from: Long, limit: Int): NSAndroidClient.ReadResponse<List<NSTreatment>> = callWrapper(dispatcher) {

        val response = api.getTreatmentsModifiedSince(from, limit)
        if (response.isSuccessful) {
            val eTagString = response.headers()["ETag"]
            val eTag = eTagString?.substring(3, eTagString.length - 1)?.toLong()
            return@callWrapper NSAndroidClient.ReadResponse(
                code = response.raw().networkResponse?.code ?: response.code(), lastServerModified = eTag, values = response.body()?.result?.map
                    (RemoteTreatment::toTreatment).toNotNull()
            )
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun getDeviceStatusModifiedSince(from: Long): List<NSDeviceStatus> = callWrapper(dispatcher) {

        val response = api.getDeviceStatusModifiedSince(from)
        if (response.isSuccessful) {
            return@callWrapper response.body()?.result?.map(RemoteDeviceStatus::toNSDeviceStatus).toNotNull()
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun createDeviceStatus(nsDeviceStatus: NSDeviceStatus): CreateUpdateResponse = callWrapper(dispatcher) {

        nsDeviceStatus.app = "AAPS"
        val response = api.createDeviceStatus(nsDeviceStatus.toRemoteDeviceStatus())
        if (response.isSuccessful) {
            if (response.code() == 200 || response.code() == 201) {
                return@callWrapper CreateUpdateResponse(
                    response = response.code(),
                    identifier = response.body()?.identifier,
                    isDeduplication = response.body()?.isDeduplication,
                    deduplicatedIdentifier = response.body()?.deduplicatedIdentifier,
                    lastModified = response.body()?.lastModified
                )
            } else throw UnknownResponseNightscoutException()
        } else return@callWrapper CreateUpdateResponse(
            response = response.code(),
            identifier = null,
            errorResponse = response.errorBody()?.string() ?: response.message()
        )
    }

    override suspend fun createTreatment(nsTreatment: NSTreatment): CreateUpdateResponse = callWrapper(dispatcher) {

        val remoteTreatment = nsTreatment.toRemoteTreatment() ?: throw InvalidFormatNightscoutException()
        remoteTreatment.app = "AAPS"
        val response = api.createTreatment(remoteTreatment)
        val errorResponse = response.errorBody()?.string()
        if (response.code() == 200 || response.code() == 201) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = response.body()?.identifier,
                isDeduplication = response.body()?.isDeduplication ?: false,
                deduplicatedIdentifier = response.body()?.deduplicatedIdentifier,
                lastModified = response.body()?.lastModified
            )
        } else if (response.code() == 400 && errorResponse?.contains("Bad or missing utcOffset field") == true && nsTreatment.utcOffset != 0L) {
            // Record can be originally uploaded without utcOffset
            // because utcOffset is mandatory and cannot be change, try 0
            nsTreatment.utcOffset = 0
            return@callWrapper createTreatment(nsTreatment)
        } else if (response.code() == 400 && errorResponse?.contains("cannot be modified by the client") == true) {
            // there is different field to field in AAPS
            // not possible to upload
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = errorResponse
            )
        } else if (response.code() in 400..499) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = errorResponse ?: response.message()
            )
        } else
            throw UnsuccessfullNightscoutException(errorResponse ?: response.message())
    }

    override suspend fun updateTreatment(nsTreatment: NSTreatment): CreateUpdateResponse = callWrapper(dispatcher) {

        // following cannot be updated
        nsTreatment.utcOffset = null
        nsTreatment.date = null
        val remoteTreatment = nsTreatment.toRemoteTreatment() ?: throw InvalidFormatNightscoutException()
        val identifier = remoteTreatment.identifier ?: throw InvalidFormatNightscoutException()
        val response =
            if (nsTreatment.isValid) api.updateTreatment(remoteTreatment, identifier)
            else api.deleteTreatment(identifier)
        if (response.isSuccessful) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                isDeduplication = false,
                deduplicatedIdentifier = null,
                lastModified = null
            )
        } else if (response.code() == 404) { // not found
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                isDeduplication = false,
                deduplicatedIdentifier = null,
                lastModified = null
            )
        } else if (response.code() in 400..499) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = response.errorBody()?.string() ?: response.message()
            )
        } else
            throw UnsuccessfullNightscoutException(response.errorBody()?.string() ?: response.message())
    }

    override suspend fun getFoods(limit: Int): NSAndroidClient.ReadResponse<List<NSFood>> = callWrapper(dispatcher) {

        val response = api.getFoods(limit)
        if (response.isSuccessful) {
            return@callWrapper NSAndroidClient.ReadResponse(
                code = response.raw().networkResponse?.code ?: response.code(),
                lastServerModified = 0,
                values = response.body()?.result?.map(RemoteFood::toNSFood).toNotNull()
            )
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    /*
        override suspend fun getFoodsModifiedSince(from: Long, limit: Int): NSAndroidClient.ReadResponse<List<NSFood>> = callWrapper(dispatcher) {

            val response = api.getFoodsModifiedSince(from, limit)
            val eTagString = response.headers()["ETag"]
            val eTag = eTagString?.substring(3, eTagString.length - 1)?.toLong() ?: throw UnsuccessfulNightscoutException()
            if (response.isSuccessful) {
                return@callWrapper NSAndroidClient.ReadResponse(eTag, response.body()?.result?.map(RemoteFood::toNSFood).toNotNull())
            } else {
                throw UnsuccessfulNightscoutException()
            }
        }
    */
    override suspend fun createFood(nsFood: NSFood): CreateUpdateResponse = callWrapper(dispatcher) {

        val remoteFood = nsFood.toRemoteFood()
        remoteFood.app = "AAPS"
        val response = api.createFood(remoteFood)
        if (response.isSuccessful) {
            if (response.code() == 200 || response.code() == 201) {
                return@callWrapper CreateUpdateResponse(
                    response = response.code(),
                    identifier = response.body()?.identifier,
                    isDeduplication = response.body()?.isDeduplication,
                    deduplicatedIdentifier = response.body()?.deduplicatedIdentifier,
                    lastModified = response.body()?.lastModified
                )
            } else throw UnsuccessfullNightscoutException()
        } else if (response.code() in 400..499) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = response.errorBody()?.string() ?: response.message()
            )
        } else
            throw UnsuccessfullNightscoutException(response.errorBody()?.string() ?: response.message())
    }

    override suspend fun updateFood(nsFood: NSFood): CreateUpdateResponse = callWrapper(dispatcher) {

        val remoteFood = nsFood.toRemoteFood()
        val identifier = nsFood.identifier ?: throw InvalidFormatNightscoutException()
        val response =
            if (nsFood.isValid) api.updateFood(remoteFood, identifier)
            else api.deleteFood(identifier)
        if (response.isSuccessful) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                isDeduplication = false,
                deduplicatedIdentifier = null,
                lastModified = null
            )
        } else if (response.code() == 404) { // not found
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                isDeduplication = false,
                deduplicatedIdentifier = null,
                lastModified = null
            )
        } else if (response.code() in 400..499) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = response.errorBody()?.string() ?: response.message()
            )
        } else
            throw UnsuccessfullNightscoutException(response.errorBody()?.string() ?: response.message())
    }

    override suspend fun createProfileStore(remoteProfileStore: JSONObject): CreateUpdateResponse = callWrapper(dispatcher) {
        remoteProfileStore.put("app", "AAPS")
        val response = api.createProfile(JsonParser.parseString(remoteProfileStore.toString()).asJsonObject)
        if (response.isSuccessful) {
            if (response.code() == 200 || response.code() == 201) {
                return@callWrapper CreateUpdateResponse(
                    response = response.code(),
                    identifier = response.body()?.identifier,
                    isDeduplication = response.body()?.isDeduplication,
                    deduplicatedIdentifier = response.body()?.deduplicatedIdentifier,
                    lastModified = response.body()?.lastModified
                )
            } else throw UnsuccessfullNightscoutException()
        } else if (response.code() in 400..499) {
            return@callWrapper CreateUpdateResponse(
                response = response.code(),
                identifier = null,
                errorResponse = response.errorBody()?.string() ?: response.message()
            )
        } else
            throw UnsuccessfullNightscoutException(response.errorBody()?.string() ?: response.message())
    }

    override suspend fun getLastProfileStore(): NSAndroidClient.ReadResponse<List<JSONObject>> = callWrapper(dispatcher) {

        val response = api.getLastProfile()
        if (response.isSuccessful) {
            val eTagString = response.headers()["ETag"]
            val eTag = eTagString?.substring(3, eTagString.length - 1)?.toLong()
            return@callWrapper NSAndroidClient.ReadResponse(code = response.raw().networkResponse?.code ?: response.code(), lastServerModified = eTag, values = response.body()?.result.toNotNull())
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    override suspend fun getProfileModifiedSince(from: Long): NSAndroidClient.ReadResponse<List<JSONObject>> = callWrapper(dispatcher) {

        val response = api.getProfileModifiedSince(from)
        if (response.isSuccessful) {
            val eTagString = response.headers()["ETag"]
            val eTag = eTagString?.substring(3, eTagString.length - 1)?.toLong()
            return@callWrapper NSAndroidClient.ReadResponse(code = response.raw().networkResponse?.code ?: response.code(), lastServerModified = eTag, values = response.body()?.result.toNotNull())
        } else if (response.code() in 400..499)
            throw InvalidParameterNightscoutException(response.errorBody()?.string() ?: response.message())
        else
            throw UnsuccessfullNightscoutException()
    }

    private suspend fun <T> callWrapper(dispatcher: CoroutineDispatcher, block: suspend () -> T): T =
        withContext(dispatcher) {
            retry(
                numberOfRetries = RETRIES,
                delayBetweenRetries = RETRY_DELAY,
                excludedExceptions = listOf(
                    InvalidAccessTokenException::class,
                    DateHeaderOutOfToleranceException::class,
                    InvalidFormatNightscoutException::class
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
