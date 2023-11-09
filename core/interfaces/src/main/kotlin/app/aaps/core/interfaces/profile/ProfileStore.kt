package app.aaps.core.interfaces.profile

import org.json.JSONObject

interface ProfileStore {

    val data: JSONObject

    fun getStartDate(): Long
    fun getDefaultProfile(): PureProfile?
    fun getDefaultProfileJson(): JSONObject?
    fun getDefaultProfileName(): String?
    fun getProfileList(): ArrayList<CharSequence>
    fun getSpecificProfile(profileName: String): PureProfile?
    val allProfilesValid: Boolean
}