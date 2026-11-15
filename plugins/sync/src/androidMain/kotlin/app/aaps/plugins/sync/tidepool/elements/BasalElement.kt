package app.aaps.plugins.sync.tidepool.elements

import app.aaps.core.interfaces.utils.DateUtil
import com.google.gson.annotations.Expose
import java.util.UUID

/**
 * A single Tidepool `basal` event representing one actual interval of basal insulin delivery.
 *
 * Tidepool renders the basal graph from these `basal` events (the `basal` schedule in `pumpSettings`
 * is only a settings snapshot, not the delivered timeline). The profile/baseline basal must therefore
 * be emitted as `deliveryType = "scheduled"` events for the profile basal line to be visible, with
 * temporary (loop) delivery emitted as `automated` and pump suspends as `suspend`.
 *
 * See https://tidepool.redocly.app/docs/device-data/data-types/basal
 */
class BasalElement private constructor(
    timestamp: Long,
    duration: Long,
    deliveryType: String,
    rate: Double?,
    scheduleName: String?,
    idSeed: String,
    dateUtil: DateUtil
) : BaseElement(timestamp, UUID.nameUUIDFromBytes(idSeed.toByteArray()).toString(), dateUtil) {

    @Expose
    internal var deliveryType: String = deliveryType

    @Expose
    internal var duration: Long = duration

    // Omitted by Gson when null: a `suspend` basal must not carry a rate per the Tidepool schema.
    @Expose
    internal var rate: Double? = rate

    // Omitted by Gson when null: only `scheduled`/`automated` carry a scheduleName, `suspend` does not.
    @Expose
    internal var scheduleName: String? = scheduleName

    @Expose
    internal var clockDriftOffset: Long = 0

    @Expose
    internal var conversionOffset: Long = 0

    init {
        type = "basal"
    }

    companion object {

        private const val SCHEDULE_NAME = "AAPS"

        /** Profile (scheduled) basal segment - the baseline basal line in Tidepool (rendered as `Manual`). */
        fun scheduled(timestamp: Long, duration: Long, rate: Double, dateUtil: DateUtil): BasalElement =
            BasalElement(timestamp, duration, "scheduled", rate, SCHEDULE_NAME, "AAPS-basal-scheduled$timestamp", dateUtil)

        /**
         * Profile-rate delivery while the loop is closed/LGS but no temporary basal overrides it.
         * Emitted as `automated` (not `scheduled`) so a closed-loop session renders as one continuous
         * `Automated` band: Tidepool draws a Manual/Automated marker at every transition between the two,
         * so emitting these as `scheduled` would flood the graph with M/A markers every loop cycle.
         */
        fun loopBaseline(timestamp: Long, duration: Long, rate: Double, dateUtil: DateUtil): BasalElement =
            BasalElement(timestamp, duration, "automated", rate, SCHEDULE_NAME, "AAPS-basal-loopbase$timestamp", dateUtil)

        /** Loop-driven (closed-loop) basal delivery during a temporary basal. */
        fun automated(timestamp: Long, duration: Long, rate: Double, tbrTimestamp: Long, dateUtil: DateUtil): BasalElement =
            BasalElement(timestamp, duration, "automated", rate, SCHEDULE_NAME, "AAPS-basal-automated$tbrTimestamp-$timestamp", dateUtil)

        /** Pump suspend / zero delivery (carries no rate per the Tidepool schema). */
        fun pumpSuspend(timestamp: Long, duration: Long, tbrTimestamp: Long, dateUtil: DateUtil): BasalElement =
            BasalElement(timestamp, duration, "suspend", null, null, "AAPS-basal-suspend$tbrTimestamp-$timestamp", dateUtil)
    }
}
