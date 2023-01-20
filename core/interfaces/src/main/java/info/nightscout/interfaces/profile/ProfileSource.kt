package info.nightscout.interfaces.profile

import androidx.fragment.app.FragmentActivity
import org.json.JSONArray

interface ProfileSource {

    class SingleProfile(
        var name: String,
        var mgdl: Boolean,
        var dia: Double,
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
            dia = dia,
            ic = JSONArray(ic.toString()),
            isf = JSONArray(isf.toString()),
            basal = JSONArray(basal.toString()),
            targetLow = JSONArray(targetLow.toString()),
            targetHigh = JSONArray(targetHigh.toString())
            )
    }

    val profile: ProfileStore?
    val profileName: String?
    fun addProfile(p: SingleProfile)
    fun copyFrom(pureProfile: PureProfile, newName: String): SingleProfile

    var currentProfileIndex: Int
    fun currentProfile(): SingleProfile?
    fun storeSettings(activity: FragmentActivity? = null, emptyCreated: Boolean = false)

}