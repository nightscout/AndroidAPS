package app.aaps.plugins.sync.nsShared

import app.aaps.core.utils.JsonHelper
import org.json.JSONObject

/**
 *
 * {"mgdl":105,"mills":1455136282375,"device":"xDrip-BluetoothWixel","direction":"Flat","filtered":98272,"unfiltered":98272,"noise":1,"rssi":100}
 */
@Suppress("SpellCheckingInspection")
class NSSgv(val data: JSONObject) {

    val mgdl: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "mgdl")
    val filtered: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "filtered")
    val noise: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "noise")
    val mills: Long?
        get() = JsonHelper.safeGetLongAllowNull(data, "mills")
    val device: String?
        get() = JsonHelper.safeGetStringAllowNull(data, "device", null)
    val direction: String?
        get() = JsonHelper.safeGetStringAllowNull(data, "direction", null)
    val id: String?
        get() = JsonHelper.safeGetStringAllowNull(data, "_id", null)

}