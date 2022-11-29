package info.nightscout.plugins.aps.loop.extensions

import info.nightscout.interfaces.pump.PumpEnactResult
import info.nightscout.interfaces.utils.Round
import org.json.JSONObject

fun PumpEnactResult.json(baseBasal: Double): JSONObject {
    val result = JSONObject()
    when {
        bolusDelivered > 0     -> {
            result.put("smb", bolusDelivered)
        }

        isTempCancel           -> {
            result.put("rate", 0)
            result.put("duration", 0)
        }

        isPercent          -> {
            // Nightscout is expecting absolute value
            val abs = Round.roundTo(baseBasal * percent / 100, 0.01)
            result.put("rate", abs)
            result.put("duration", duration)
        }

        else               -> {
            result.put("rate", absolute)
            result.put("duration", duration)
        }
    }
    return result
}
