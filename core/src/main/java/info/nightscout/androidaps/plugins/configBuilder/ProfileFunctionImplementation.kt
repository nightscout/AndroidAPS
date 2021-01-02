package info.nightscout.androidaps.plugins.configBuilder

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Constants
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.db.ProfileSwitch
import info.nightscout.androidaps.db.Source
import info.nightscout.androidaps.interfaces.ProfileStore
import info.nightscout.androidaps.interfaces.ActivePluginProvider
import info.nightscout.androidaps.interfaces.ProfileFunction
import info.nightscout.androidaps.interfaces.TreatmentsInterface
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.utils.DateUtil
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import java.security.spec.InvalidParameterSpecException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileFunctionImplementation @Inject constructor(
    private val injector: HasAndroidInjector,
    private val aapsLogger: AAPSLogger,
    private val sp: SP,
    private val resourceHelper: ResourceHelper,
    private val activePlugin: ActivePluginProvider,
    private val fabricPrivacy: FabricPrivacy
) : ProfileFunction {

    override fun getProfileName(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = false)

    override fun getProfileName(customized: Boolean): String =
        getProfileName(System.currentTimeMillis(), customized, showRemainingTime = false)

    override fun getProfileName(time: Long, customized: Boolean, showRemainingTime: Boolean): String {
        var profileName = resourceHelper.gs(R.string.noprofileselected)

        val activeTreatments = activePlugin.activeTreatments
        val activeProfile = activePlugin.activeProfileInterface

        val profileSwitch = activeTreatments.getProfileSwitchFromHistory(time)
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                profileName = if (customized) profileSwitch.customizedName else profileSwitch.profileName
            } else {
                activeProfile.profile?.let { profileStore ->
                    val profile = profileStore.getSpecificProfile(profileSwitch.profileName)
                    if (profile != null)
                        profileName = profileSwitch.profileName
                }
            }

            if (showRemainingTime && profileSwitch.durationInMinutes != 0) {
                profileName += DateUtil.untilString(profileSwitch.originalEnd(), resourceHelper)
            }
        }
        return profileName
    }

    override fun getProfileNameWithDuration(): String =
        getProfileName(System.currentTimeMillis(), customized = true, showRemainingTime = true)

    override fun isProfileValid(from: String): Boolean =
        getProfile()?.isValid(from) ?: false

    override fun getProfile(): Profile? =
        getProfile(System.currentTimeMillis())

    override fun getProfile(time: Long): Profile? = getProfile(time, activePlugin.activeTreatments)

    override fun getProfile(time: Long, activeTreatments: TreatmentsInterface): Profile? {
        val activeProfile = activePlugin.activeProfileInterface

        //log.debug("Profile for: " + new Date(time).toLocaleString() + " : " + getProfileName(time));
        val profileSwitch = activeTreatments.getProfileSwitchFromHistory(time)
        if (profileSwitch != null) {
            if (profileSwitch.profileJson != null) {
                return profileSwitch.profileObject
            } else if (activeProfile.profile != null) {
                val profile = activeProfile.profile!!.getSpecificProfile(profileSwitch.profileName)
                if (profile != null) return profile
            }
        }
        if (activeTreatments.profileSwitchesFromHistory.size() > 0) {
            val bundle = Bundle()
            bundle.putString(FirebaseAnalytics.Param.ITEM_LIST_ID, "CatchedError")
            bundle.putString(FirebaseAnalytics.Param.START_DATE, time.toString())
            bundle.putString(FirebaseAnalytics.Param.ITEM_LIST_NAME, activeTreatments.profileSwitchesFromHistory.toString())
            fabricPrivacy.logCustom(bundle)
        }
        aapsLogger.error("getProfile at the end: returning null")
        return null
    }

    override fun getUnits(): String =
        sp.getString(R.string.key_units, Constants.MGDL)

    override fun prepareProfileSwitch(profileStore: ProfileStore, profileName: String, duration: Int, percentage: Int, timeShift: Int, date: Long): ProfileSwitch {
        val profile = profileStore.getSpecificProfile(profileName)
            ?: throw InvalidParameterSpecException(profileName)
        val profileSwitch = ProfileSwitch(injector)
        profileSwitch.date = date
        profileSwitch.source = Source.USER
        profileSwitch.profileName = profileName
        profileSwitch.profileJson = profile.data.toString()
        profileSwitch.durationInMinutes = duration
        profileSwitch.isCPP = percentage != 100 || timeShift != 0
        profileSwitch.timeshift = timeShift
        profileSwitch.percentage = percentage
        return profileSwitch
    }
}