package info.nightscout.androidaps.plugins.configBuilder

import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.data.ProfileStore
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.security.spec.InvalidParameterSpecException
import javax.inject.Singleton

@Singleton
class ProfileFunctionImplementation constructor(private val sp: SP) : ProfileFunction {

    override fun getProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = false)

    override fun getProfileName(customized: Boolean): String =
        getProfileName(System.currentTimeMillis(), customized, showRemainingTime = false)

    override fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String =
        ProfileFunctions.getInstance().getProfileName(time, customized, showRemainingTime)

    override fun getProfileNameWithDuration(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = true)

    override fun isProfileValid(from: String): Boolean =
        getProfile()?.isValid(from) ?: false

    override fun getProfile(): Profile? {
        return ProfileFunctions.getInstance().getProfile()
    }

    override fun getProfile(time: Long): Profile? =
        ProfileFunctions.getInstance().getProfile(System.currentTimeMillis())

    override fun getUnits(): String =
        sp.getString(R.string.key_units, Constants.MGDL)

    override fun prepareProfileSwitch(profileStore: ProfileStore, profileName: String, duration: Int, percentage: Int, timeShift: Int, date: Long): ProfileSwitch {
        val profile = profileStore.getSpecificProfile(profileName)
            ?: throw InvalidParameterSpecException(profileName)
        val profileSwitch = ProfileSwitch()
        profileSwitch.date = date
        profileSwitch.source = Source.USER
        profileSwitch.profileName = profileName
        profileSwitch.profileJson = profile.data.toString()
        profileSwitch.profilePlugin = ConfigBuilderPlugin.getPlugin().activeProfileInterface::class.java.name
        profileSwitch.durationInMinutes = duration
        profileSwitch.isCPP = percentage != 100 || timeShift != 0
        profileSwitch.timeshift = timeShift
        profileSwitch.percentage = percentage
        return profileSwitch
    }
}