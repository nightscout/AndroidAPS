package app.aaps.interfaces.profile

import app.aaps.interfaces.db.GlucoseUnit
import info.nightscout.database.entities.data.Block
import info.nightscout.database.entities.data.TargetBlock
import org.json.JSONObject
import java.util.TimeZone

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