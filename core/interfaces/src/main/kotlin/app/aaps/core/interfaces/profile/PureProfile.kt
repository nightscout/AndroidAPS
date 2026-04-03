package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.ICfg
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import org.json.JSONObject
import java.util.TimeZone

/**
 * Pure profile like it's entered by user. Contains only data and it's serialized version in JSON
 */
class PureProfile(
    /** Source json data (must correspond to the rest of the profile) */
    var jsonObject: JSONObject,
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    var iCfg: ICfg? = null,
    var glucoseUnit: GlucoseUnit,
    var timeZone: TimeZone
)