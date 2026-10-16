package app.aaps.core.objects.extensions

import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.utils.DateUtil
import org.json.JSONObject

/**
 * `org.json` form of [toJsonObject], for the callers that still hold a `JSONObject`.
 *
 * Going back through the text is not just plumbing - it is what keeps the written bytes identical.
 * `org.json` renders a whole-numbered Double as a bare integer (`100.0` becomes `100`) and kotlinx
 * renders `100.0`. Reparsing here lets `org.json` re-normalise on the way out, so the
 * `entries<date>.json` file that autotune reads does not change at all.
 *
 * A non-finite `sgv` still fails loudly, as it did before: `org.json` refuses NaN and Infinity, and
 * it refuses them on this reparse just as it used to refuse them on the original put.
 */
fun GV.toJson(isAdd: Boolean, dateUtil: DateUtil): JSONObject =
    JSONObject(toJsonObject(isAdd, dateUtil).toString())
