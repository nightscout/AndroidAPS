package info.nightscout.androidaps

import androidx.collection.ArrayMap
import dagger.android.HasAndroidInjector
import info.nightscout.core.extensions.pureProfileFromJson
import info.nightscout.core.profile.ProfileSealed
import info.nightscout.interfaces.Config
import info.nightscout.interfaces.plugin.ActivePlugin
import info.nightscout.interfaces.profile.ProfileStore
import info.nightscout.interfaces.profile.PureProfile
import info.nightscout.interfaces.utils.HardLimits
import info.nightscout.interfaces.utils.JsonHelper
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.shared.interfaces.ResourceHelper
import info.nightscout.shared.utils.DateUtil
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class ProfileStoreObject(val injector: HasAndroidInjector, override val data: JSONObject, val dateUtil: DateUtil) : ProfileStore {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var config: Config
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var hardLimits: HardLimits

    init {
        injector.androidInjector().inject(this)
    }

    private val cachedObjects = ArrayMap<String, PureProfile>()

    private fun storeUnits(): String? = JsonHelper.safeGetStringAllowNull(data, "units", null)

    private fun getStore(): JSONObject? {
        try {
            if (data.has("store")) return data.getJSONObject("store")
        } catch (e: JSONException) {
            aapsLogger.error("Unhandled exception", e)
        }
        return null
    }

    override fun getStartDate(): Long {
        val iso = JsonHelper.safeGetString(data, "startDate") ?: return 0
        return try {
            dateUtil.fromISODateString(iso)
        } catch (e: Exception) {
            0
        }
    }

    override fun getDefaultProfile(): PureProfile? = getDefaultProfileName()?.let { getSpecificProfile(it) }
    override fun getDefaultProfileJson(): JSONObject? = getDefaultProfileName()?.let { getSpecificProfileJson(it) }

    override fun getDefaultProfileName(): String? {
        val defaultProfileName = data.optString("defaultProfile")
        return if (defaultProfileName.isNotEmpty()) getStore()?.has(defaultProfileName)?.let { defaultProfileName } else null
    }

    override fun getProfileList(): ArrayList<CharSequence> {
        val ret = ArrayList<CharSequence>()
        getStore()?.keys()?.let { keys ->
            while (keys.hasNext()) {
                val profileName = keys.next() as String
                ret.add(profileName)
            }
        }
        return ret
    }

    @Synchronized
    override fun getSpecificProfile(profileName: String): PureProfile? {
        var profile: PureProfile? = null
        val units = JsonHelper.safeGetStringAllowNull(data, "units", storeUnits())
        getStore()?.let { store ->
            if (store.has(profileName)) {
                profile = cachedObjects[profileName]
                if (profile == null) {
                    JsonHelper.safeGetJSONObject(store, profileName, null)?.let { profileObject ->
                        profile = pureProfileFromJson(profileObject, dateUtil, units)
                        profile?.let { cachedObjects[profileName] = profile }
                    }
                }
            }
        }
        return profile
    }

    private fun getSpecificProfileJson(profileName: String): JSONObject? {
        getStore()?.let { store ->
            if (store.has(profileName))
                return JsonHelper.safeGetJSONObject(store, profileName, null)
        }
        return null
    }

    override val allProfilesValid: Boolean
        get() = getProfileList()
            .asSequence()
            .map { profileName -> getSpecificProfile(profileName.toString()) }
            .map { pureProfile -> pureProfile?.let { ProfileSealed.Pure(pureProfile).isValid("allProfilesValid", activePlugin.activePump, config, rh, rxBus, hardLimits, false) } }
            .all { it?.isValid == true }
}
