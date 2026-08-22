package app.aaps.core.objects.extensions

import app.aaps.core.data.model.ICfg
import app.aaps.core.utils.lenientDouble
import app.aaps.core.utils.lenientLong
import app.aaps.core.utils.lenientString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun ICfg.toJsonObject(): JsonObject = buildJsonObject {
    put("insulinLabel", insulinLabel)
    put("insulinEndTime", insulinEndTime)
    put("insulinPeakTime", insulinPeakTime)
    put("concentration", JsonPrimitive(concentration))
    put("insulinNickname", insulinNickname)
}

/**
 * used to restore configuration within InsulinPlugin and insulin Editor
 *
 * Reads leniently on purpose. `longOrNull` is `content.toLongOrNull()`, which answers null for
 * `1.8E7` and for `18000000.5` - forms the `org.json` reader this replaced accepted, because it
 * coerced through `Double`. Falling back to 0 there would mean `insulinEndTime == 0`, i.e. DIA 0, so
 * IOB would decay at once and the loop would believe there is no insulin on board. See
 * [lenientLong].
 */
fun ICfg.Companion.fromJsonObject(json: JsonObject): ICfg {
    val icfg = ICfg(
        insulinLabel = json.lenientString("insulinLabel"),
        insulinEndTime = json.lenientLong("insulinEndTime"),
        insulinPeakTime = json.lenientLong("insulinPeakTime"),
        concentration = json.lenientDouble("concentration", 1.0)
    )

    icfg.insulinNickname = json.lenientString("insulinNickname")

    return icfg
}
