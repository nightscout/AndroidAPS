package app.aaps.core.nssdk.localmodel.clientcontrol

import kotlinx.serialization.Serializable

/**
 * The master's computed bolus preview, returned in a [AckEnvelope.payload] for a `BolusPrepare` ack.
 *
 * The client renders the master's color-coded confirmation [lines] for the user to confirm, then echoes
 * [bolusId] in the matching `BolusCommit`. JSON-serialized into the signed ack payload, so it is tamper-proof
 * — a forged dose is no more believable than a forged "Ok". The dose is computed + constraint-capped on the
 * master (the client never sends a raw number), so client-side staleness cannot inflate it.
 *
 * [advisorApplies] tells the client to offer the high-BG "correct now, eat later" choice (showing
 * [advisorLines] instead of the normal [lines]); the user's pick rides back in `BolusCommit.asAdvisor`.
 */
@Serializable
data class BolusPreview(
    val bolusId: Long,
    val lines: List<ConfirmationLineDto> = emptyList(),
    val advisorApplies: Boolean = false,
    val advisorLines: List<ConfirmationLineDto> = emptyList()
)

/**
 * One confirmation line on the wire — the master's `ConfirmationLine` reduced to its role NAME plus the
 * already-localized text, so the client renders the master's exact color-coded wizard confirmation through
 * the same dialog. The text is built in the master's locale (the same locale for a single-user master+client).
 */
@Serializable
data class ConfirmationLineDto(
    val role: String,
    val text: String
)
