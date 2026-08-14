package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock

/**
 * Pure profile like it's entered by user. Contains only data.
 *
 * It used to carry the source JSON alongside the blocks. Nothing read it — every consumer worked
 * from the blocks — while every construction paid to build it, so it is gone. Render on demand with
 * `Profile.toPureNsJson` when JSON is actually needed.
 */
class PureProfile(
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    var iCfg: ICfg? = null,
    var glucoseUnit: GlucoseUnit,
    /**
     * Offset from UTC in milliseconds, **for the moment the profile was built**.
     *
     * This was a whole `java.util.TimeZone`, but the only thing ever read off it was
     * `rawOffset` - the zone's *standard* offset, which ignores daylight saving by definition. So
     * `Europe/Prague` gave +01:00 even in July. The single consumer turns the number back into a zone
     * name for the Nightscout profile document by looking for a zone at that offset **now**, and in
     * July no European zone is at +01:00, so the document named an unrelated zone instead.
     *
     * Holding the resolved offset makes that impossible: the value is DST aware at the point it is
     * taken, matching `utcOffset` on every other record and [app.aaps.core.data.time.systemUtcOffsetAt].
     * Nothing is lost that was not already lost - the zone's identity went away the moment `rawOffset`
     * reduced it to a number.
     */
    var utcOffset: Long
)