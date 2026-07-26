package app.aaps.core.interfaces.profile

import org.json.JSONArray

/**
 * One entry in the local profile list.
 *
 * Pairs a [name] with profile data ([ic], [isf], [basal], target ranges) and the glucose
 * unit flag ([mgdl]). The [JSONArray] fields hold the per-hour-block schedules in the same
 * shape as Nightscout profile JSON, so they round-trip cleanly through the profile store.
 *
 * Use [deepClone] before mutating an instance you've received from [ProfileRepository] —
 * the repository holds references and the `JSONArray`s are mutable.
 */
class SingleProfile(
    var name: String,
    var mgdl: Boolean,
    var ic: JSONArray,
    var isf: JSONArray,
    var basal: JSONArray,
    var targetLow: JSONArray,
    var targetHigh: JSONArray,
) {

    fun deepClone(): SingleProfile =
        SingleProfile(
            name = name,
            mgdl = mgdl,
            ic = JSONArray(ic.toString()),
            isf = JSONArray(isf.toString()),
            basal = JSONArray(basal.toString()),
            targetLow = JSONArray(targetLow.toString()),
            targetHigh = JSONArray(targetHigh.toString())
        )
}
