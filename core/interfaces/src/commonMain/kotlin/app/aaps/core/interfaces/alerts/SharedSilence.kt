package app.aaps.core.interfaces.alerts

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.abs

/**
 * A silence of a BG alarm given on one phone for every phone: "Silence everywhere" (`_docs/ALARMS.md`, step 2f).
 *
 * The phones share one set of BG rules, so each one raises the same alarm from the same readings; the silence names it
 * by [source], [key] and the time of its occurrence ([raisedAt]), and holds until [until]. [sentAt] is the sender's
 * clock when it was given: a silence from a phone whose clock is ahead is refused ([accepts]).
 *
 * The master keeps the list of the silences that hold now in `StringNonKey.AlertSharedSilences` and sends it to every
 * client in its hot status doc; a client sends its own to the master over the client-control link. The phone that
 * silences does it at once on itself, never waiting for this way round: a client that is not connected still stops
 * ringing.
 */
@Serializable
data class SharedSilence(
    val source: String,
    val key: String,
    val raisedAt: Long,
    val until: Long,
    val sentAt: Long
) {

    /** Whether this phone, its clock at [now], takes this silence: not from a clock far ahead, not over, not too long. */
    fun accepts(now: Long): Boolean =
        sentAt <= now + MAX_CLOCK_AHEAD_MS && until > now && until - now <= MAX_LENGTH_MS

    /** Whether it is for the occurrence of an alert raised at [alertRaisedAt]: the phones see the same readings. */
    fun isFor(source: String, key: String, alertRaisedAt: Long): Boolean =
        this.source == source && this.key == key && abs(alertRaisedAt - raisedAt) <= RAISED_AT_TOLERANCE_MS

    companion object {

        /** A silence from a phone whose clock is this far ahead of this one is refused. */
        const val MAX_CLOCK_AHEAD_MS = 5 * 60_000L

        /** No silence is longer than the longest one the alarm screen offers. */
        const val MAX_LENGTH_MS = 240 * 60_000L

        /**
         * The phones raise the same alarm from the same readings, but a rate or a predicted low takes its time from the
         * reading that raised it, and a phone may get that reading a moment later.
         */
        const val RAISED_AT_TOLERANCE_MS = 10 * 60_000L

        private val listSerializer = ListSerializer(serializer())
        private val json = Json { ignoreUnknownKeys = true }

        /** The stored list; nothing readable is an empty list. */
        fun parse(text: String): List<SharedSilence> = runCatching { json.decodeFromString(listSerializer, text) }.getOrDefault(emptyList())

        fun toJson(list: List<SharedSilence>): String = json.encodeToString(listSerializer, list)

        /** [list] with [silence] added: one per alarm, the newest given wins; the ones that are over are dropped. */
        fun merge(list: List<SharedSilence>, silence: SharedSilence, now: Long): List<SharedSilence> =
            (list.filterNot { it.source == silence.source && it.key == silence.key } + silence).filter { it.until > now }
    }
}

/**
 * The client-control link, for "Silence everywhere" on a client (`_docs/ALARMS.md`, step 2f). Implemented by the
 * Nightscout client; the alert core sends through it and knows nothing of Nightscout.
 */
interface SharedSilenceLink {

    /**
     * Whether a silence can be given for every phone from here: on the master, client control is on and a client is
     * paired; on a client, it is paired, client control is on and its role may silence the master's alarms.
     */
    fun available(): Boolean

    /** Client: sends [silence] to the master, which applies it and passes it to the other clients. Fire and forget. */
    fun send(silence: SharedSilence)
}
