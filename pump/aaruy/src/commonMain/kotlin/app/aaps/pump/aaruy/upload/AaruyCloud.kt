package app.aaps.pump.aaruy.upload

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.pump.aaruy.database.HistoryUnitInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlin.math.abs

/** The HTTP engine of the platform: OkHttp on Android and the desktop, NSURLSession on iOS. */
internal expect val aaruyHttpEngine: HttpClientEngineFactory<*>

/**
 * One entry of the pump log as the vendor server takes it. The API is known only from the vendor app (PR 5589), so the
 * fields are what that app sent.
 * @param index goes up with each record of a pump, see [AaruyCloud.lastIndex]
 * @param productCode the model, see `AaruyType.productCode`
 * @param recordTime seconds since 1970
 * @param kind 0 basal, 1 temp basal, 2 bolus, 3 extended bolus, 110 a new pump, 203 alarm, 204 rewind, 205 prime,
 * 206 factory reset, 207 pause
 * @param value a rate or an amount in units, the position of the piston, or for the other kinds the number from the pump
 * @param seconds a bolus: how long it took. Otherwise the minutes of the entry, in seconds.
 */
internal data class AaruyCloudRecord(
    val index: Int,
    val productCode: String,
    val serial: String,
    val recordTime: Long,
    val kind: Int,
    val value: String,
    val seconds: Long,
)

private const val HISTORY_BOLUS = 2
private const val KIND_EXTENDED_BOLUS = 3
private const val KIND_PRIME = 205

/** The record the vendor app made of this entry. An extended bolus sends only its extended amount, as that app did. */
internal fun HistoryUnitInfo.toCloudRecord(index: Int, productCode: String): AaruyCloudRecord {
    val extended = type == HISTORY_BOLUS && injectTime > 0
    val kind = when {
        type in 3..7 -> type + 200
        extended     -> KIND_EXTENDED_BOLUS
        else         -> type
    }
    val value = when {
        kind < KIND_EXTENDED_BOLUS   -> decimal(data, 3)
        kind == KIND_EXTENDED_BOLUS  -> decimal(delayBolus, 3)
        kind == KIND_PRIME           -> decimal(data, 2)
        else                         -> data.toString()
    }
    val seconds = if (type == HISTORY_BOLUS && !extended) immTime else injectTime * 60
    return AaruyCloudRecord(index, productCode, blueName, time / 1000, kind, value, seconds)
}

/**
 * [value] in units of 10^-[digits], written as the vendor app wrote a number: a point, no zeros at the end, but at least
 * one digit after the point. 1500 thousandths is "1.5", 1000 is "1.0". The same on every phone, whatever its language.
 */
internal fun decimal(value: Long, digits: Int): String {
    var scale = 1L
    repeat(digits) { scale *= 10 }
    val sign = if (value < 0) "-" else ""
    val whole = abs(value) / scale
    val fraction = (abs(value) % scale).toString().padStart(digits, '0').trimEnd('0').ifEmpty { "0" }
    return "$sign$whole.$fraction"
}

/** The two calls of the vendor server that the vendor app made for the pump log. Both send a form and get JSON back. */
internal class AaruyCloud(
    private val http: HttpClient,
    private val aapsLogger: AAPSLogger,
    private val baseUrl: String = BASE_URL,
) {

    companion object {

        const val BASE_URL = "https://api.aaruy.com/"
    }

    /** The index of the newest record the server has of [serial]. 0 when it has none, null when it could not be asked. */
    suspend fun lastIndex(serial: String): Int? {
        val reply = post("open/aaps/ruypump/record/last", listOf("sn" to serial)) ?: return null
        return reply.dataIndex() ?: 0
    }

    /**
     * True when the server took [record]. The vendor app took an answer with index 0 as "not taken" and stopped there.
     * UNVERIFIED what the server means by it.
     */
    suspend fun send(record: AaruyCloudRecord): Boolean {
        val reply = post(
            "open/aaps/ruypump/notice", listOf(
                "index" to record.index.toString(),
                "product_code" to record.productCode,
                "sn" to record.serial,
                "record_time" to record.recordTime.toString(),
                "kind" to record.kind.toString(),
                "value" to record.value,
                "seconds" to record.seconds.toString(),
            )
        ) ?: return false
        return (reply.dataIndex() ?: 0) != 0
    }

    /** `data.index` of the answer. */
    private fun JsonObject.dataIndex(): Int? = ((this["data"] as? JsonObject)?.get("index") as? JsonPrimitive)?.intOrNull

    /** The answer, or null for no connection, an HTTP error or an answer that is not a JSON object. */
    private suspend fun post(path: String, fields: List<Pair<String, String>>): JsonObject? = try {
        val response = http.submitForm(baseUrl + path, parameters { fields.forEach { (name, value) -> append(name, value) } })
        if (!response.status.isSuccess()) {
            aapsLogger.warn(LTag.PUMPCOMM, "Upload: $path answered ${response.status.value}")
            null
        } else Json.parseToJsonElement(response.bodyAsText()) as? JsonObject
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        aapsLogger.warn(LTag.PUMPCOMM, "Upload: $path failed: ${e.message}")
        null
    }
}
