package app.aaps.pump.omnipod.common.ui.compose

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.ui.compose.StatusLevel
import app.aaps.core.ui.compose.pump.PumpInfoRow
import app.aaps.pump.omnipod.common.R
import kotlin.time.Duration.Companion.hours

private const val PLACEHOLDER = "-"

/** How long before a time its row starts to warn. */
private val WARNING_TIME = 4.hours.inWholeMilliseconds

/**
 * The "Pod Expires" and "Pod Hard End" rows of the pod overview, the same for every Omnipod driver.
 *
 * The colour shows what happens if the user does nothing, not only how close a time is (#5365):
 * - Pod Expires turns yellow 4 h before the expiry. It never turns red, because the pod keeps
 *   delivering after it.
 * - Pod Hard End turns yellow at the expiry, when the grace period starts, and red 4 h before the
 *   hard end, because at the hard end the pod stops delivering insulin.
 *
 * During the grace period the hard end row also shows the time left, so the user does not have to
 * compare it with the time on the pod.
 *
 * @param now current time in milliseconds
 * @param expiresAt end of the nominal pod life in milliseconds, or null while it is not known
 * @param hardEndAt time when the pod stops delivering, in milliseconds, or null while it is not known
 * @param formatTime formats [expiresAt] and [hardEndAt] for display. Each driver shows times on the pod in its own way.
 */
fun podExpiryRows(
    rh: ResourceHelper,
    dateUtil: DateUtil,
    now: Long,
    expiresAt: Long?,
    hardEndAt: Long?,
    formatTime: (Long) -> String
): List<PumpInfoRow> {
    val hardEndLevel = hardEndLevel(now, expiresAt, hardEndAt)
    val hardEndValue = when {
        hardEndAt == null                                     -> PLACEHOLDER
        hardEndLevel != StatusLevel.NORMAL && now < hardEndAt ->
            rh.gs(R.string.omnipod_common_overview_pod_hard_end_value, formatTime(hardEndAt), dateUtil.timeRemainingString(hardEndAt - now, rh))

        else                                                  -> formatTime(hardEndAt)
    }
    return listOf(
        PumpInfoRow(
            label = rh.gs(R.string.omnipod_common_overview_pod_expiry_date),
            value = expiresAt?.let(formatTime) ?: PLACEHOLDER,
            level = expiryLevel(now, expiresAt)
        ),
        PumpInfoRow(label = rh.gs(R.string.omnipod_common_overview_pod_hard_end_date), value = hardEndValue, level = hardEndLevel)
    )
}

internal fun expiryLevel(now: Long, expiresAt: Long?): StatusLevel = when {
    expiresAt != null && now >= expiresAt - WARNING_TIME -> StatusLevel.WARNING
    else                                                 -> StatusLevel.NORMAL
}

internal fun hardEndLevel(now: Long, expiresAt: Long?, hardEndAt: Long?): StatusLevel = when {
    hardEndAt != null && now >= hardEndAt - WARNING_TIME -> StatusLevel.CRITICAL
    expiresAt != null && now >= expiresAt                -> StatusLevel.WARNING
    else                                                 -> StatusLevel.NORMAL
}
