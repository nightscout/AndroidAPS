package app.aaps.core.interfaces.profile

import androidx.collection.ArrayMap
import app.aaps.core.data.model.ICfg
import org.json.JSONObject

interface ProfileStore {

    val data: JSONObject

    fun getStartDate(): Long
    fun getDefaultProfile(): PureProfile?
    fun getDefaultProfileJson(): JSONObject?
    fun getDefaultProfileName(): String?
    fun getProfileList(): ArrayList<CharSequence>
    fun getProfileList(iCfg: ICfg): ArrayList<String>
    fun getSpecificProfile(profileName: String): PureProfile?
    val allProfilesValid: Boolean
}