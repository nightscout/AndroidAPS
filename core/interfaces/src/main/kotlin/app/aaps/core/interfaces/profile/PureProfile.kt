package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import java.util.TimeZone

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
    var timeZone: TimeZone
)