package app.aaps.core.objects.extensions

import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.utils.DateUtil
import org.json.JSONArray
import org.json.JSONObject

/**
 * `org.json` forms of [jsonObject] and [determineBasalJsonObject], for the callers that still hold an
 * `org.json` document.
 *
 * Reparsing through the text is what keeps the produced bytes identical: `org.json` renders a whole
 * numbered Double as a bare integer (`10.0` becomes `10`) and a negative zero as `-0`, and kotlinx
 * renders `10.0`. Letting `org.json` re-render on the way out means the uploaded device status does
 * not change at all.
 */

fun IobTotal.json(dateUtil: DateUtil): JSONObject =
    JSONObject(jsonObject(dateUtil).toString())

fun IobTotal.determineBasalJson(dateUtil: DateUtil): JSONObject =
    JSONObject(determineBasalJsonObject(dateUtil).toString())

fun Array<IobTotal>.convertToJSONArray(dateUtil: DateUtil): JSONArray {
    val array = JSONArray()
    for (i in this.indices) {
        array.put(this[i].determineBasalJson(dateUtil))
    }
    return array
}
