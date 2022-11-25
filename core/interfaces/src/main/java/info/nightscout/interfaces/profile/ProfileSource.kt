package info.nightscout.interfaces.profile

import androidx.fragment.app.FragmentActivity
import info.nightscout.interfaces.Constants
import org.json.JSONArray

interface ProfileSource {

    class SingleProfile {

        var name: String? = null
        var mgdl: Boolean = false
        var dia: Double = Constants.defaultDIA
        var ic: JSONArray? = null
        var isf: JSONArray? = null
        var basal: JSONArray? = null
        var targetLow: JSONArray? = null
        var targetHigh: JSONArray? = null

        fun deepClone(): SingleProfile {
            val sp = SingleProfile()
            sp.name = name
            sp.mgdl = mgdl
            sp.dia = dia
            sp.ic = JSONArray(ic.toString())
            sp.isf = JSONArray(isf.toString())
            sp.basal = JSONArray(basal.toString())
            sp.targetLow = JSONArray(targetLow.toString())
            sp.targetHigh = JSONArray(targetHigh.toString())
            return sp
        }
    }

    val profile: ProfileStore?
    val profileName: String?
    fun addProfile(p: SingleProfile)
    fun copyFrom(pureProfile: PureProfile, newName: String): SingleProfile

    var currentProfileIndex: Int
    fun currentProfile(): SingleProfile?
    fun storeSettings(activity: FragmentActivity? = null)

}