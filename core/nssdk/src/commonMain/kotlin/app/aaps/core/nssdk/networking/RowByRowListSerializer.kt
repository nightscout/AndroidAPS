package app.aaps.core.nssdk.networking

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder

/** One row of a page from Nightscout: the decoded [value], or the [error] why this one row could not be read. */
internal class WireRow<T>(val value: T?, val error: String?)

/**
 * Reads a JSON list of documents row by row. A row that cannot be read becomes a [WireRow] with its error,
 * and the other rows of the page still arrive.
 *
 * Read as one list, a single bad document failed the whole page, and the treatment history stopped there
 * for good: the first load took the failed page for its end (#5626). Such a document comes from another
 * uploader, or from an older AAPS (see [app.aaps.core.nssdk.remotemodel.WireDurationSerializer]).
 *
 * Only for reading: pages from Nightscout are never written back.
 */
internal class RowByRowListSerializer<T>(private val rowSerializer: KSerializer<T>) : KSerializer<List<WireRow<T>>> {

    override val descriptor: SerialDescriptor = ListSerializer(rowSerializer).descriptor

    override fun deserialize(decoder: Decoder): List<WireRow<T>> {
        val json = decoder as? JsonDecoder ?: throw SerializationException("A page from Nightscout is read from JSON only")
        val rows = json.decodeJsonElement() as? JsonArray ?: throw SerializationException("A page from Nightscout must be a list")
        return rows.map { row ->
            try {
                WireRow(json.json.decodeFromJsonElement(rowSerializer, row), null)
            } catch (e: IllegalArgumentException) {
                // SerializationException is one, and so is the NumberFormatException of a bad number
                WireRow(null, "${e.message}: ${row.toString().take(MAX_ROW_IN_ERROR)}")
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<WireRow<T>>) =
        throw SerializationException("A page from Nightscout is only read, never written")

    private companion object {

        /** A treatment can carry a whole profile; the start of the row is enough to find it in Nightscout. */
        const val MAX_ROW_IN_ERROR = 300
    }
}
