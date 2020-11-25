package info.nightscout.androidaps.interfaces

import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.db.ProfileSwitch

interface ProfileFunction {
    fun getProfileName(): String
    fun getProfileName(customized: Boolean): String
    fun getProfileNameWithDuration(): String
    fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String
    fun isProfileValid(from: String): Boolean
    fun getProfile(): Profile?
    fun getUnits(): String
    fun getProfile(time: Long): Profile?
    fun getProfile(time: Long, activeTreatments: TreatmentsInterface): Profile?
    fun prepareProfileSwitch(profileStore: ProfileStore, profileName: String, duration: Int, percentage: Int, timeShift: Int, date: Long): ProfileSwitch
}