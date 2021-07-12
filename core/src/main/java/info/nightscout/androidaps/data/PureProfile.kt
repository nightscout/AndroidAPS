package info.nightscout.androidaps.data

import info.nightscout.androidaps.database.data.Block
import info.nightscout.androidaps.database.data.TargetBlock
import info.nightscout.androidaps.interfaces.GlucoseUnit
import org.json.JSONObject
import java.util.*

class PureProfile(
    var jsonObject: JSONObject, // source json data (must correspond to the rest of the profile)
    var basalBlocks: List<Block>,
    var isfBlocks: List<Block>,
    var icBlocks: List<Block>,
    var targetBlocks: List<TargetBlock>,
    var dia: Double,
    var glucoseUnit: GlucoseUnit,
    var timeZone: TimeZone
)