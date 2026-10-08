package app.aaps.core.interfaces.profile

import kotlinx.serialization.json.JsonObject

interface ProfileStore {

    fun with(data: JsonObject): ProfileStore

    fun getData(): JsonObject
    fun getStartDate(): Long
    fun getDefaultProfile(): PureProfile?
    fun getDefaultProfileName(): String?
    fun getProfileList(): ArrayList<CharSequence>
    fun getSpecificProfile(profileName: String): PureProfile?
    val allProfilesValid: Boolean
}