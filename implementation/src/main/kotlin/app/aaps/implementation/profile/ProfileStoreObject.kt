package app.aaps.implementation.profile

import androidx.collection.ArrayMap
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class ProfileStoreObject @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val rh: ResourceHelper,
    private val rxBus: RxBus,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil
) : ProfileStore {

    private lateinit var data: JSONObject

    override fun with(data: JSONObject): ProfileStore = this.also {
        this.data = data
    }

    override fun getData(): JSONObject = data

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
        val iso = JsonHelper.safeGetString(data, "created_at") ?: JsonHelper.safeGetString(data, "startDate") ?: return 0
        return try {
            dateUtil.fromISODateString(iso)
        } catch (_: Exception) {
            0
        }
    }

    override fun getDefaultProfile(): PureProfile? = getDefaultProfileName()?.let { getSpecificProfile(it) }
    override fun getDefaultProfileJson(): JSONObject? =
        getDefaultProfileName()?.let { getSpecificProfileJson(it) }

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
            .map { pureProfile -> pureProfile?.let { ProfileSealed.Pure(pureProfile, activePlugin).isValid("allProfilesValid", activePlugin.activePump, config, rh, rxBus, hardLimits, false) } }
            .all { it?.isValid == true }
}