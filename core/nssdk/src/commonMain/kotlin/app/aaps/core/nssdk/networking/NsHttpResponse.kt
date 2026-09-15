package app.aaps.core.nssdk.networking

import app.aaps.core.nssdk.nsSdkJson
import kotlinx.serialization.DeserializationStrategy

/**
 * One HTTP response, with the body already read into a string.
 *
 * Reading the body **once, up front** is the whole point of this type. Retrofit handed out a
 * `Response<T>` with a separate `body()` and `errorBody()`, and the client leans on that: an error
 * body is inspected as raw text to decide whether to re-send a record with `utcOffset = 0`
 * (`NSAndroidClientImpl.createSgv`). Ktor has no such split - the body is a stream that can be
 * consumed only once - so a port that reads it lazily, or reads it only on success, silently loses
 * that text and drops the record for good.
 *
 * Holding the text also means the status ladders in `NSAndroidClientImpl` can stay as they are.
 *
 * Bodies here are small: single documents, or pages already limited by a `limit` parameter.
 */
internal class NsHttpResponse<T>(
    val code: Int,
    private val eTagHeader: String?,
    /** Raw response text, always read, success or not. */
    val bodyText: String,
    private val deserializer: DeserializationStrategy<T>
) {

    val isSuccessful: Boolean get() = code in 200..299

    /**
     * The decoded body, or null if it cannot be decoded.
     *
     * Null rather than a throw, because that is what Retrofit's `body()` did for an error response,
     * and several call sites read `response.body()?.identifier` on paths that also accept failure.
     */
    fun body(): T? =
        if (bodyText.isEmpty()) null
        else runCatching { nsSdkJson.decodeFromString(deserializer, bodyText) }.getOrNull()

    /**
     * The raw text of a failed response, matching Retrofit's `errorBody()?.string()`.
     *
     * Null for a successful response so the callers' `errorResponse ?: response.message()` fallbacks
     * behave as before.
     */
    fun errorBody(): String? = if (isSuccessful) null else bodyText.ifEmpty { null }

    /** Stand-in for Retrofit's `Response.message()`, used only as a fallback when there is no body. */
    fun message(): String = "HTTP $code"

    /**
     * `lastServerModified`, parsed from the weak ETag Nightscout sends (`W/"<millis>"`).
     *
     * The arithmetic is copied exactly from the Retrofit version, including the fact that it is
     * positional rather than a pattern match, so a header in any other shape still throws. Real
     * Nightscout always sends the weak form; `NsSdkResponseContractTest` pins both cases.
     */
    fun eTagAsLong(): Long? = eTagHeader?.substring(3, eTagHeader.length - 1)?.toLong()
}
