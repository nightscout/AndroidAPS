package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.remotemodel.LastModified
import app.aaps.core.nssdk.remotemodel.NSResponse
import app.aaps.core.nssdk.remotemodel.RemoteCreateUpdateResponse
import app.aaps.core.nssdk.remotemodel.RemoteDeviceStatus
import app.aaps.core.nssdk.remotemodel.RemoteEntry
import app.aaps.core.nssdk.remotemodel.RemoteFood
import app.aaps.core.nssdk.remotemodel.RemoteStatusResponse
import app.aaps.core.nssdk.remotemodel.RemoteTreatment
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject

/**
 * The Nightscout API v3 endpoints, on Ktor.
 *
 * Replaces the Retrofit interface. The method names, parameters and order are kept identical so the
 * change in `NSAndroidClientImpl` is a type swap rather than a rewrite - the point is that the
 * contract tests stay meaningful, not that the code gets prettier.
 *
 * ### Two URL rules that matter
 *
 * **Query keys keep their `$`.** `date$gt`, `created_at$gt` and `sort$desc` are Nightscout operators,
 * and Retrofit was told `encoded = true` so they went out untouched. Ktor percent-encodes by default,
 * and `date%24gt` is simply a different, unknown filter - Nightscout ignores it and answers **200
 * with unfiltered rows**. That is why every query below goes through [URLBuilder.encodedParameters]
 * rather than `parameters`.
 *
 * **Path segments are encoded.** Identifiers come from the server and may contain characters that
 * would otherwise create a new path segment, so [appendPathSegments] (which encodes) is used, exactly
 * as Retrofit's `@Path` did by default.
 *
 * `NsSdkUrlContractTest` pins the resulting URL for every endpoint, as a literal string.
 */
internal class NightscoutApi(
    private val client: HttpClient,
    /** Fully formed, e.g. `https://host/api/` - see `NsUrl.toBaseUrl`. */
    private val baseUrl: String
) {

    // ---------------------------------------------------------------- plumbing

    private fun HttpRequestBuilder.nsUrl(vararg segments: String, query: URLBuilder.() -> Unit = {}) {
        url {
            takeFrom(baseUrl)
            // encodeSlash = true so an identifier containing "/" stays ONE segment and addresses one
            // document, instead of silently becoming a different path. Retrofit's @Path did this by
            // default (it produced "a%2Fb"); Ktor's default does not, so it has to be asked for.
            appendPathSegments(segments.toList(), encodeSlash = true)
            query()
        }
    }

    /**
     * Issues the request and reads the body **once**, whatever the status.
     *
     * See [NsHttpResponse] - the client inspects error bodies as raw text, so the body cannot be
     * read lazily or only on success.
     */
    private suspend fun <T> call(
        method: HttpMethod,
        deserializer: DeserializationStrategy<T>,
        block: HttpRequestBuilder.() -> Unit
    ): NsHttpResponse<T> {
        val response = client.request {
            this.method = method
            block()
        }
        return NsHttpResponse(
            code = response.status.value,
            eTagHeader = response.headers["ETag"],
            bodyText = response.bodyAsText(),
            deserializer = deserializer
        )
    }

    private inline fun <reified T : Any> HttpRequestBuilder.jsonBody(value: T) {
        contentType(ContentType.Application.Json)
        setBody(value)
    }

    // ---------------------------------------------------------------- status

    suspend fun statusSimple() =
        call(HttpMethod.Get, NSResponse.serializer(RemoteStatusResponse.serializer())) { nsUrl("v3", "status") }

    suspend fun lastModified() =
        call(HttpMethod.Get, NSResponse.serializer(LastModified.serializer())) { nsUrl("v3", "lastModified") }

    // ---------------------------------------------------------------- entries

    private val entryListSerializer = NSResponse.serializer(ListSerializer(RemoteEntry.serializer()))

    suspend fun getSgvs() = call(HttpMethod.Get, entryListSerializer) {
        nsUrl("v3", "entries") {
            encodedParameters.append("sort\$desc", "date")
            encodedParameters.append("type", "sgv")
        }
    }

    suspend fun getSgvsNewerThan(date: Long, limit: Int) = call(HttpMethod.Get, entryListSerializer) {
        nsUrl("v3", "entries") {
            encodedParameters.append("sort", "date")
            encodedParameters.append("date\$gt", date.toString())
            encodedParameters.append("limit", limit.toString())
        }
    }

    suspend fun getSgvsModifiedSince(from: Long, limit: Int) = call(HttpMethod.Get, entryListSerializer) {
        nsUrl("v3", "entries", "history", from.toString()) {
            encodedParameters.append("limit", limit.toString())
        }
    }

    suspend fun createEntry(remoteEntry: RemoteEntry) =
        call(HttpMethod.Post, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "entries"); jsonBody(remoteEntry)
        }

    suspend fun updateEntry(remoteEntry: RemoteEntry, identifier: String) =
        call(HttpMethod.Patch, NSResponse.serializer(RemoteCreateUpdateResponse.serializer())) {
            nsUrl("v3", "entries", identifier); jsonBody(remoteEntry)
        }

    suspend fun deleteEntry(identifier: String) =
        call(HttpMethod.Delete, NSResponse.serializer(RemoteCreateUpdateResponse.serializer())) {
            nsUrl("v3", "entries", identifier)
        }

    // ---------------------------------------------------------------- treatments

    private val treatmentListSerializer = NSResponse.serializer(ListSerializer(RemoteTreatment.serializer()))

    suspend fun getTreatmentsNewerThan(createdAt: String, limit: Int) = call(HttpMethod.Get, treatmentListSerializer) {
        nsUrl("v3", "treatments") {
            encodedParameters.append("sort", "created_at")
            encodedParameters.append("created_at\$gt", createdAt)
            encodedParameters.append("limit", limit.toString())
        }
    }

    suspend fun getTreatmentsModifiedSince(from: Long, limit: Int) = call(HttpMethod.Get, treatmentListSerializer) {
        nsUrl("v3", "treatments", "history", from.toString()) {
            encodedParameters.append("limit", limit.toString())
        }
    }

    suspend fun createTreatment(remoteTreatment: RemoteTreatment) =
        call(HttpMethod.Post, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "treatments"); jsonBody(remoteTreatment)
        }

    suspend fun updateTreatment(remoteTreatment: RemoteTreatment, identifier: String) =
        call(HttpMethod.Patch, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "treatments", identifier); jsonBody(remoteTreatment)
        }

    suspend fun deleteTreatment(identifier: String) =
        call(HttpMethod.Delete, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "treatments", identifier)
        }

    // ---------------------------------------------------------------- device status

    suspend fun createDeviceStatus(remoteDeviceStatus: RemoteDeviceStatus) =
        call(HttpMethod.Post, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "devicestatus"); jsonBody(remoteDeviceStatus)
        }

    suspend fun getDeviceStatusModifiedSince(from: Long) =
        call(HttpMethod.Get, NSResponse.serializer(ListSerializer(RemoteDeviceStatus.serializer()))) {
            nsUrl("v3", "devicestatus", "history", from.toString())
        }

    // ---------------------------------------------------------------- food

    suspend fun getFoods(limit: Int) =
        call(HttpMethod.Get, NSResponse.serializer(ListSerializer(RemoteFood.serializer()))) {
            nsUrl("v3", "food") { encodedParameters.append("limit", limit.toString()) }
        }

    suspend fun createFood(remoteFood: RemoteFood) =
        call(HttpMethod.Post, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "food"); jsonBody(remoteFood)
        }

    /**
     * Food update and delete **deliberately do not send a request. Do not "fix" these.**
     *
     * The Retrofit versions declared an `{identifier}` parameter that was missing from the path, so
     * Retrofit refused to build them and no request was ever made - the throw is swallowed by the
     * broad catch in `NSClientV3Plugin`, and food edits have never synced. The matching endpoint is
     * broken on the Nightscout side, so sending would not help.
     *
     * Writing the "obvious" URL here would turn "never sends" into `PATCH /api/v3/food` and
     * `DELETE /api/v3/food` **with no identifier** - a request against the whole collection on a
     * live Nightscout. So the local failure is reproduced on purpose, keeping today's behaviour.
     * When Nightscout supports these, change both sides together.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun updateFood(remoteFood: RemoteFood, identifier: String): NsHttpResponse<RemoteCreateUpdateResponse> =
        error("v3/food update is not supported by Nightscout - no request is sent, see NightscoutApi.updateFood")

    @Suppress("UNUSED_PARAMETER")
    suspend fun deleteFood(identifier: String): NsHttpResponse<RemoteCreateUpdateResponse> =
        error("v3/food delete is not supported by Nightscout - no request is sent, see NightscoutApi.updateFood")

    // ---------------------------------------------------------------- profile

    private val jsonObjectListSerializer = NSResponse.serializer(ListSerializer(JsonObject.serializer()))

    suspend fun getProfileModifiedSince(from: Long, limit: Int = 10) = call(HttpMethod.Get, jsonObjectListSerializer) {
        nsUrl("v3", "profile", "history", from.toString()) {
            encodedParameters.append("limit", limit.toString())
        }
    }

    suspend fun getLastProfile() = call(HttpMethod.Get, jsonObjectListSerializer) {
        nsUrl("v3", "profile") {
            encodedParameters.append("sort\$desc", "date")
            encodedParameters.append("limit", "1")
        }
    }

    suspend fun createProfile(profile: JsonObject) =
        call(HttpMethod.Post, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "profile"); jsonBody(profile)
        }

    // ---------------------------------------------------------------- settings

    suspend fun getSetting(identifier: String) =
        call(HttpMethod.Get, NSResponse.serializer(JsonObject.serializer())) {
            nsUrl("v3", "settings", identifier)
        }

    suspend fun getSettingsModifiedSince(from: Long, limit: Int = 100) = call(HttpMethod.Get, jsonObjectListSerializer) {
        nsUrl("v3", "settings", "history", from.toString()) {
            encodedParameters.append("limit", limit.toString())
        }
    }

    suspend fun searchSettings(limit: Int = 100) = call(HttpMethod.Get, jsonObjectListSerializer) {
        nsUrl("v3", "settings") { encodedParameters.append("limit", limit.toString()) }
    }

    suspend fun createSetting(settings: JsonObject) =
        call(HttpMethod.Post, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "settings"); jsonBody(settings)
        }

    suspend fun patchSetting(settings: JsonObject, identifier: String) =
        call(HttpMethod.Patch, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "settings", identifier); jsonBody(settings)
        }

    /** PUT (NS3 "UPDATE") = upsert. Replaces an existing doc, or inserts if absent. */
    suspend fun updateSetting(settings: JsonObject, identifier: String) =
        call(HttpMethod.Put, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "settings", identifier); jsonBody(settings)
        }

    /**
     * [permanent] `null` -> soft delete (tombstone); `true` -> NS `?permanent=true` hard delete.
     * A null must send **no parameter at all** - `permanent=false` would be a different request.
     */
    suspend fun deleteSetting(identifier: String, permanent: Boolean?) =
        call(HttpMethod.Delete, RemoteCreateUpdateResponse.serializer()) {
            nsUrl("v3", "settings", identifier) {
                permanent?.let { encodedParameters.append("permanent", it.toString()) }
            }
        }
}
