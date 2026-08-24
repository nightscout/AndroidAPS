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
import app.aaps.core.utils.JsonHelper.safeGetJSONObject
import app.aaps.core.utils.JsonHelper.safeGetString
import app.aaps.core.utils.JsonHelper.safeGetStringAllowNull
import kotlinx.serialization.json.JsonObject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import javax.inject.Inject

// Unscoped, matching the Dagger @Binds it replaces: a profile store is a value object and each caller
// builds its own with `with(...)`.
@ContributesBinding(AppScope::class)
class ProfileStoreObject @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val config: Config,
    private val rh: ResourceHelper,
    private val notificationManager: NotificationManager,
    private val hardLimits: HardLimits,
    private val dateUtil: DateUtil
) : ProfileStore {

    private lateinit var data: JsonObject

    /**
     * kotlinx all the way through now.
     *
     * This used to convert the incoming document to `org.json` and work on that, because the readers
     * it leaned on - `JsonHelper` and `pureProfileFromJson` - were `org.json` only. Both speak kotlinx
     * today, so the document is kept as it arrives and the two text round trips are gone.
     */
    override fun with(data: JsonObject): ProfileStore = this.also {
        this.data = data
    }

    /**
     * The store itself.
     *
     * Handing out the stored object is safe because a kotlinx tree is immutable. The previous version
     * handed out a live `JSONObject`, and both sync selectors wrote into it
     * (`profileJson.put("date", …)`), mutating the store they had just read; callers build the amended
     * copy they actually wanted instead. While [data] was still `org.json` this had to re-parse from
     * text on every call to hand out a fresh copy - that is no longer needed.
     */
    override fun getData(): JsonObject = data

    private val cachedObjects = ArrayMap<String, PureProfile>()

    private fun storeUnits(): String? = data.safeGetStringAllowNull("units", null)

    private fun getStore(): JsonObject? {
        val store = data["store"] ?: return null
        // A `store` that is not an object used to throw out of getJSONObject and get logged. Keep the
        // log: silently answering null would hide a malformed document.
        if (store !is JsonObject) {
            aapsLogger.error("Malformed profile store: 'store' is not an object")
            return null
        }
        return store
    }

    override fun getStartDate(): Long {
        val iso = data.safeGetString("created_at") ?: data.safeGetString("startDate") ?: return 0
        return try {
            dateUtil.fromISODateString(iso)
        } catch (_: Exception) {
            0
        }
    }

    override fun getDefaultProfile(): PureProfile? = getDefaultProfileName()?.let { getSpecificProfile(it) }
    override fun getDefaultProfileJson(): JsonObject? = getDefaultProfileName()?.let { getSpecificProfileJson(it) }

    override fun getDefaultProfileName(): String? {
        // optString answered "" for a missing key, and the literal text "null" for an explicit null.
        // Either way the name failed the lookup below and this returned null, which is what the
        // empty-string default reproduces.
        val defaultProfileName = data.safeGetString("defaultProfile", "")
        return if (defaultProfileName.isNotEmpty()) getStore()?.containsKey(defaultProfileName)?.let { defaultProfileName } else null
    }

    override fun getProfileList(): ArrayList<CharSequence> =
        ArrayList<CharSequence>().also { ret -> getStore()?.keys?.forEach { ret.add(it) } }

    @Synchronized
    override fun getSpecificProfile(profileName: String): PureProfile? {
        var profile: PureProfile? = null
        val units = data.safeGetStringAllowNull("units", storeUnits())
        getStore()?.let { store ->
            if (store.containsKey(profileName)) {
                profile = cachedObjects[profileName]
                if (profile == null) {
                    store.safeGetJSONObject(profileName, null)?.let { profileObject ->
                        profile = pureProfileFromJson(profileObject, dateUtil, units)
                        profile?.let { cachedObjects[profileName] = profile }
                    }
                }
            }
        }
        return profile
    }

    private fun getSpecificProfileJson(profileName: String): JsonObject? =
        getStore()?.let { store ->
            if (store.containsKey(profileName)) store.safeGetJSONObject(profileName, null) else null
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