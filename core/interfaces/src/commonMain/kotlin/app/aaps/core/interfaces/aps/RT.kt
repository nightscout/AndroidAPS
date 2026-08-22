package app.aaps.core.interfaces.aps

import app.aaps.core.data.datetime.parseIsoToEpochMillisOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlin.time.Instant

@Serializable
data class RT(
    var algorithm: APSResult.Algorithm = APSResult.Algorithm.UNKNOWN,
    var runningDynamicIsf: Boolean,
    @Serializable(with = TimestampToIsoSerializer::class)
    var timestamp: Long? = null,
    val temp: String = "absolute",
    var bg: Double? = null,
    var tick: String? = null,
    var eventualBG: Double? = null,
    var targetBG: Double? = null,
    var snoozeBG: Double? = null, // AMA only
    var insulinReq: Double? = null,
    var carbsReq: Int? = null,
    var carbsReqWithin: Int? = null,
    var units: Double? = null, // micro bolus
    @Serializable(with = TimestampToIsoSerializer::class)
    var deliverAt: Long? = null, // The time at which the micro bolus should be delivered
    var sensitivityRatio: Double? = null, // autosens ratio (fraction of normal basal)
    @Serializable(with = StringBuilderSerializer::class)
    var reason: StringBuilder = StringBuilder(),
    var duration: Int? = null,
    var rate: Double? = null,
    var predBGs: Predictions? = null,
    var COB: Double? = null,
    var IOB: Double? = null,
    var variable_sens: Double? = null,
    var isfMgdlForCarbs: Double? = null, // used to pass to AAPS client

    var consoleLog: MutableList<String>? = null,
    var consoleError: MutableList<String>? = null
) {

    fun serialize() = Json.encodeToString(serializer(), this)

    object StringBuilderSerializer : KSerializer<StringBuilder> {

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("StringBuilder", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: StringBuilder) {
            encoder.encodeString(value.toString())
        }

        override fun deserialize(decoder: Decoder): StringBuilder {
            return StringBuilder().append(decoder.decodeString())
        }
    }

    object TimestampToIsoSerializer : KSerializer<Long> {

        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LongToIso", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: Long) {
            encoder.encodeString(toISOString(value))
        }

        override fun deserialize(decoder: Decoder): Long {
            return fromISODateString(decoder.decodeString())
        }

        /**
         * Was joda's `ISODateTimeFormat.dateTimeParser()`. The replacement accepts the same shapes -
         * this parses device status written by other Nightscout uploaders, not only what AAPS wrote,
         * so `+0200` without a colon and offset-less values have to keep working.
         *
         * It still throws on input it cannot read, as joda did. A silent epoch-0 timestamp inside an
         * APS result would be worse than a loud failure.
         */
        fun fromISODateString(isoDateString: String): Long =
            parseIsoToEpochMillisOrNull(isoDateString)
                ?: throw IllegalArgumentException("Invalid format: $isoDateString")

        /**
         * `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'` in UTC, always with three fractional digits.
         *
         * Built explicitly rather than with `Instant.toString()`, which drops trailing zeros and
         * omits the fraction altogether on a whole second.
         */
        private val isoOut = LocalDateTime.Format {
            year(); char('-'); monthNumber(); char('-'); day()
            char('T')
            hour(); char(':'); minute(); char(':'); second()
            char('.'); secondFraction(3)
        }

        /**
         * This used to be `SimpleDateFormat(pattern, Locale.getDefault())`, which resolves its
         * calendar from the locale. On a phone set to Thai that selects the Buddhist calendar and
         * the year was written as 2569 instead of 2026 - a wire timestamp 543 years in the future.
         * The formatter here is locale independent, which fixes that as well as removing the JVM
         * dependency. `RtIsoStringParityTest` pins both halves.
         */
        fun toISOString(date: Long): String =
            isoOut.format(Instant.fromEpochMilliseconds(date).toLocalDateTime(TimeZone.UTC)) + "Z"
    }

    companion object {

        private val serializer = Json { ignoreUnknownKeys = true }
        fun deserialize(jsonString: String) = serializer.decodeFromString(serializer(), jsonString)
    }
}