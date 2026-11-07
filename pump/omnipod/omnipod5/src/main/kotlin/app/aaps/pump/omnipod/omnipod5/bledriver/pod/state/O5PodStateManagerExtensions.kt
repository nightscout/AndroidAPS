package app.aaps.pump.omnipod.omnipod5.bledriver.pod.state

import app.aaps.pump.omnipod.common.bledriver.pod.definition.ActivationProgress
import app.aaps.pump.omnipod.common.bledriver.pod.definition.BasalProgram
import app.aaps.pump.omnipod.common.bledriver.pod.definition.DeliveryStatus
import app.aaps.pump.omnipod.common.bledriver.pod.definition.PodConstants
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.TimeZone

/**
 * Estimated pod expiration time, mirroring
 * `OmnipodDashPodStateManagerImpl.expiry`'s formula (including its unexplained
 * `.minusHours(8)` grace-period adjustment, copied verbatim rather than guessed at) -
 * null until [O5PodStateManager.podLifeInHours]/[O5PodStateManager.minutesSinceActivation]
 * are known (i.e. before [app.aaps.pump.omnipod.common.bledriver.pod.response
 * .SetUniqueIdResponse] has been received during activation).
 */
val O5PodStateManager.expiry: ZonedDateTime?
    get() {
        val hours = podLifeInHours ?: return null
        val minutes = minutesSinceActivation ?: return null
        val lastUpdated = lastStatusResponseReceived ?: return null
        return ZonedDateTime.now()
            .plusHours(hours.toLong())
            .minusMinutes(minutes.toLong())
            .minus(Duration.ofMillis(System.currentTimeMillis() - lastUpdated))
            .minusHours(8)
    }


/**
 * Wall-clock epoch millis the pod was activated. Prefers [O5PodStateManager.podActivatedAt]
 * (read from status page 5) and falls back to the same derivation Dash uses when that page
 * has not been fetched: the time of the last status response minus
 * [O5PodStateManager.minutesSinceActivation]. Null when neither is known.
 */
val O5PodStateManager.activationTime: Long?
    get() = podActivatedAt ?: run {
        val minutes = minutesSinceActivation ?: return null
        val lastUpdated = lastStatusResponseReceived ?: return null
        lastUpdated - minutes * 60_000L
    }

/**
 * The current time on the pod's own clock, in the time zone the pod was last set to.
 * The pod has no wall clock, so this is rebuilt the same way
 * [app.aaps.pump.omnipod.common.bledriver.pod.state.OmnipodDashPodStateManagerImpl.time]
 * does it: activation instant, plus the elapsed minutes the pod reported, plus the time
 * that has passed since that report arrived. Null until the pod clock's time zone is
 * known, which happens the first time a basal program is sent.
 */
val O5PodStateManager.time: ZonedDateTime?
    get() {
        val activatedAt = activationTime ?: return null
        val minutes = minutesSinceActivation ?: return null
        val offset = timeZoneOffset ?: return null
        val lastUpdated = lastStatusResponseReceived ?: return null
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(activatedAt), ZoneId.ofOffset("", ZoneOffset.ofTotalSeconds(offset / 1000)))
            .plusMinutes(minutes.toLong())
            .plus(Duration.ofMillis(System.currentTimeMillis() - lastUpdated))
    }

/** How far [time] runs ahead (positive) or behind (negative) the phone clock. */
val O5PodStateManager.timeDrift: Duration?
    get() = time?.let { Duration.between(ZonedDateTime.now(), it) }


/** [O5PodStateManager.totalPulsesDelivered] minus pulses already attributed to boluses -
 *  null until both are known (i.e. before activation completes and
 *  [O5PodStateManager.cumulativeBolusPulsesDelivered] is initialized). */
val O5PodStateManager.basalPulsesDelivered: Short?
    get() {
        val total = totalPulsesDelivered ?: return null
        val bolus = cumulativeBolusPulsesDelivered ?: return null
        return (total - bolus).toShort()
    }

val O5PodStateManager.basalDelivered: Double
    get() = (basalPulsesDelivered ?: 0) * PodConstants.POD_PULSE_BOLUS_UNITS

/** Positive = over-delivery, negative = under-delivery, relative to [O5PodStateManager
 *  .basalExpected]. */
val O5PodStateManager.basalDrift: Double
    get() = basalDelivered - (basalExpected ?: basalDelivered)

/**
 * Integrates expected basal delivery (units) over `[startTime, endTime]`, accounting for
 * an active temp basal overriding the scheduled program during its window. Mirrors
 * `OmnipodDashPodStateManagerImpl.integrateExpectedDelivery`, simplified: O5 doesn't track
 * a separate pod-timezone offset the way Dash does (every other O5 basal-rate lookup -
 * see [app.aaps.pump.omnipod.omnipod5.O5PumpPlugin.baseBasalRate] - already calls
 * [BasalProgram.rateAt] directly against device-local time, so this does the same rather
 * than introducing a cross-conversion no other O5 code performs).
 */
fun integrateO5ExpectedDelivery(
    startTime: Long,
    endTime: Long,
    activeTempBasalStartTime: Long?,
    activeTempBasalRate: Double?,
    activeTempBasalDurationMinutes: Short?,
    basalProgram: BasalProgram?
): Double? {
    if (startTime > endTime) return null

    val tempBasalEnd = if (activeTempBasalStartTime != null && activeTempBasalDurationMinutes != null)
        activeTempBasalStartTime + activeTempBasalDurationMinutes * 60_000L
    else null

    val boundaries = mutableSetOf(startTime, endTime)
    if (activeTempBasalStartTime != null && tempBasalEnd != null) {
        if (activeTempBasalStartTime in startTime until endTime) boundaries.add(activeTempBasalStartTime)
        if (tempBasalEnd in startTime until endTime) boundaries.add(tempBasalEnd)
    }
    basalProgram?.segments?.forEach { segment ->
        val deviceOffsetMs = TimeZone.getDefault().getOffset(startTime)
        val dayStartLocal = ((startTime + deviceOffsetMs) / 86400_000L) * 86400_000L - deviceOffsetMs
        var segmentStart = dayStartLocal + segment.startSlotIndex.toLong() * 30 * 60_000L
        if (segmentStart <= startTime) segmentStart += 86400_000L
        while (segmentStart < endTime) {
            boundaries.add(segmentStart)
            segmentStart += 86400_000L
        }
    }

    return boundaries.sorted().windowed(2).map { (boundaryStart, boundaryEnd) ->
        val segmentHours = (boundaryEnd - boundaryStart) / 3600_000.0
        val segmentMid = (boundaryStart + boundaryEnd) / 2
        val rate = if (activeTempBasalStartTime != null && activeTempBasalRate != null && tempBasalEnd != null &&
            segmentMid in activeTempBasalStartTime until tempBasalEnd
        ) {
            activeTempBasalRate
        } else {
            basalProgram?.rateAt(segmentMid) ?: return null
        }
        rate * segmentHours
    }.sum()
}

/** Integrates [integrateO5ExpectedDelivery] into [O5PodStateManager.basalExpected] since
 *  its last update, or seeds it from [basalDelivered] the first time this is called after
 *  activation completes - call from every `updateFromDefaultStatusResponse`, matching
 *  [OmnipodDashPodStateManagerImpl.updatePodState]'s equivalent step. [previousUpdate] must
 *  be the OLD [O5PodStateManager.lastStatusResponseReceived], captured before it's
 *  overwritten with the new response's timestamp. */
fun O5PodStateManager.nextBasalExpected(previousUpdate: Long?, now: Long): Double? =
    basalExpected?.let { current ->
        previousUpdate?.let { start ->
            integrateO5ExpectedDelivery(
                start, now, activeTempBasalStartTime, activeTempBasalRate, activeTempBasalDurationMinutes, basalProgram
            )?.let { delta -> current + delta }
        } ?: current
    } ?: basalDelivered.takeIf { activationProgress == ActivationProgress.COMPLETED }

/** Save acceptance with the status itself, before a later stop can replace its sequence. */
internal fun O5PodStateManager.PendingDoseCommand.withPodAcceptance(
    podSequence: Short,
    status: DeliveryStatus
): O5PodStateManager.PendingDoseCommand {
    val sequenceMatches = bolusCancelRequested != true &&
        sequenceNumber?.let { (it.toInt() and 0x0f) == (podSequence.toInt() and 0x0f) } == true
    val bolusActive = type == O5PodStateManager.PendingDoseType.BOLUS && status.bolusDeliveringActive()
    return if (!confirmedByStatus && (sequenceMatches || bolusActive)) copy(confirmedByStatus = true) else this
}
