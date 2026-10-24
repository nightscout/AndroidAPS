package app.aaps.plugins.aps.loop.extensions

import app.aaps.core.interfaces.pump.PumpEnactResult
import app.aaps.core.interfaces.utils.Round
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the pump actually enacted, as the `enacted` fragment of the uploaded device status.
 *
 * Exactly one of the three shapes, in this order:
 *  - a bolus was delivered - `smb` only, **no** `rate` or `duration`
 *  - the temp was cancelled - integer `0` for both
 *  - a temp is running - `rate` (absolute, even when the pump reported a percentage) and `duration`
 *
 * `put("rate", 0)` is an **Int** on the cancel branch, and it has to stay one: the caller copies the
 * value straight into the uploaded document, where `0.0` would be a different number to what
 * Nightscout has always received.
 *
 * Was `org.json` and Android-only. Nothing of this document reaches the wire - the caller reads two
 * values out of it and discards it - so moving to kotlinx changed no uploaded byte.
 */
fun PumpEnactResult.jsonObject(baseBasal: Double): JsonObject =
    buildJsonObject {
        when {
            bolusDelivered > 0 -> put("smb", bolusDelivered)

            isTempCancel       -> {
                put("rate", 0)
                put("duration", 0)
            }

            isPercent          -> {
                // Nightscout expects an absolute rate
                put("rate", Round.roundTo(baseBasal * percent / 100, 0.01))
                put("duration", duration)
            }

            else               -> {
                put("rate", absolute)
                put("duration", duration)
            }
        }
    }
