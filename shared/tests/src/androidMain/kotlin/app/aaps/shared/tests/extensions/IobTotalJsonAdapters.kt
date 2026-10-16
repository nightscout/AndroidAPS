package app.aaps.shared.tests.extensions

import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.objects.extensions.determineBasalJsonObject
import app.aaps.core.objects.extensions.jsonObject
import org.json.JSONArray
import org.json.JSONObject

/**
 * `org.json` forms of [jsonObject] and [determineBasalJsonObject], for **test** code that needs an
 * `org.json` document.
 *
 * Lives in `:shared:tests` because that is the only kind of caller left: the Rhino algorithm adapters
 * in `:app` androidTest, which hand these to a JavaScript engine, and `IobTotalTest`. Production
 * builds the same documents with kotlinx.
 *
 * It used to sit in `:core:objects` androidMain and re-render through the text so the uploaded device
 * status kept `org.json` number formatting - `10.0` printed as `10`. That no longer matters:
 * Nightscout parses the value into a JavaScript number, where `10` and `10.0` are the same thing, and
 * `roundInsulinForDisplayFormat` does arithmetic on it before display.
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
