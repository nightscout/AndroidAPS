package app.aaps.core.interfaces.profile

import org.json.JSONObject

interface ProfileStore {

    fun with(data: JSONObject): ProfileStore

    fun getData(): JSONObject
    fun getStartDate(): Long
    fun getDefaultProfile(): PureProfile?
    fun getDefaultProfileJson(): JSONObject?
    fun getDefaultProfileName(): String?
    fun getProfileList(): ArrayList<CharSequence>
    fun getSpecificProfile(profileName: String): PureProfile?
    val allProfilesValid: Boolean
}