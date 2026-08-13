package app.aaps.implementation.profile

import androidx.collection.ArrayMap
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.JsonHelper
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject

class ProfileStoreObject @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val rh: ResourceHelper,
    private val notificationManager: NotificationManager,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil
) : ProfileStore {

    private lateinit var data: JSONObject

    /**
     * Converts once at the boundary and keeps working on `org.json` inside.
     *
     * The interface speaks kotlinx so it can move to common code; the reading below still leans on
     * `JsonHelper` and `pureProfileFromJson`, which are `org.json` throughout and are their own
     * migration. This is the same inside-out step used for the profile block parsers: the contract
     * crosses first, the internals follow.
     */
    override fun with(data: JsonObject): ProfileStore = this.also {
        this.data = JSONObject(data.toString())
    }

    /**
     * The store as kotlinx, rebuilt on each call.
     *
     * Deliberately not a view of [data]: the previous version handed out the live `JSONObject`, and
     * both sync selectors wrote into it (`profileJson.put("date", …)`), mutating the store they had
     * just read. A kotlinx tree is immutable, so that cannot happen - the callers now build the
     * amended copy they actually wanted.
     */
    override fun getData(): JsonObject = Json.parseToJsonElement(data.toString()).jsonObject

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
    override fun getDefaultProfileJson(): JsonObject? =
        getDefaultProfileName()?.let { getSpecificProfileJson(it) }?.let { Json.parseToJsonElement(it.toString()).jsonObject }

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
        // Sync/storage gate: only semantic (pump-independent) validity. A profile that is merely
        // incompatible with the *current* pump must still upload to Nightscout, otherwise a single
        // pump-specific basal value (or a pump switch) silently blocks the whole profile store.
        get() = getProfileList()
            .asSequence()
            .map { profileName -> getSpecificProfile(profileName.toString()) }
            .map { pureProfile -> pureProfile?.let { ProfileSealed.Pure(pureProfile, activePlugin).validateSemantic(rh, hardLimits) } }
            .all { it?.isValid == true }
}