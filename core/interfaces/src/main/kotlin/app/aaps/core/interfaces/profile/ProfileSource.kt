package app.aaps.core.interfaces.profile

import androidx.fragment.app.FragmentActivity
import org.json.JSONArray

interface ProfileSource {

    /**
     * SingleProfile stores a name of a profile in addition to PureProfile
     */
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

    /**
     * Convert [PureProfile] to [SingleProfile]
     *
     * @param pureProfile PureProfile
     * @param newName Name
     * @return SingleProfile
     */
    fun copyFrom(pureProfile: PureProfile, newName: String): SingleProfile

    /**
     * Currently edited profile in store as index
     */
    var currentProfileIndex: Int

    /**
     * Get currently edited profile from store as [SingleProfile]
     *
     * @return currently selected profile
     */
    fun currentProfile(): SingleProfile?

    /**
     * Store active [ProfileStore] to SharedPreferences
     *
     * @param activity context for error dialog
     * @param timestamp timestamp of latest change
     */
    fun storeSettings(activity: FragmentActivity? = null, timestamp: Long)

    /**
     * Import [ProfileStore] to memory and and save to SharedPreferences
     *
     * @param store ProfileStore to import
     */
    fun loadFromStore(store: ProfileStore)

}