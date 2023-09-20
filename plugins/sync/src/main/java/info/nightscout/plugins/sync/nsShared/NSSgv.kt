package info.nightscout.plugins.sync.nsShared

import info.nightscout.core.utils.JsonHelper
import info.nightscout.interfaces.nsclient.NSSgv
import org.json.JSONObject

/**
 *
 * {"mgdl":105,"mills":1455136282375,"device":"xDrip-BluetoothWixel","direction":"Flat","filtered":98272,"unfiltered":98272,"noise":1,"rssi":100}
 */
@Suppress("SpellCheckingInspection")
class NSSgvObject(val data: JSONObject) : NSSgv {

    override val mgdl: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "mgdl")
    override val filtered: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "filtered")
    override val unfiltered: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "unfiltered")
    override val noise: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "noise")
    override val rssi: Int?
        get() = JsonHelper.safeGetIntAllowNull(data, "rssi")
    override val mills: Long?
        get() = JsonHelper.safeGetLongAllowNull(data, "mills")
    override val device: String?
        get() = JsonHelper.safeGetStringAllowNull(data, "device", null)
    override val direction: String?
        get() = JsonHelper.safeGetStringAllowNull(data, "direction", null)
    override val id: String?
        get() = JsonHelper.safeGetStringAllowNull(data, "_id", null)

}