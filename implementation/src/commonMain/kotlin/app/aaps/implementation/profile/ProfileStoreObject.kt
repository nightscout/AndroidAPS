package app.aaps.implementation.profile

import app.aaps.core.interfaces.concurrent.AapsLock
import app.aaps.core.interfaces.concurrent.withLock
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.objects.extensions.pureProfileFromJson
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.utils.safeGetJSONObject
import app.aaps.core.utils.safeGetString
import app.aaps.core.utils.safeGetStringAllowNull
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.serialization.json.JsonObject

@ContributesBinding(AppScope::class)
class ProfileStoreObject @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val activePlugin: ActivePlugin,
    private val rh: TextResolver,
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

    private val lock = AapsLock()
    private val cachedObjects = mutableMapOf<String, PureProfile>()

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

    override fun getDefaultProfileName(): String? {
        // optString answered "" for a missing key, and the literal text "null" for an explicit null.
        // Either way the name failed the lookup below and this returned null, which is what the
        // empty-string default reproduces.
        val defaultProfileName = data.safeGetString("defaultProfile", "")
        return if (defaultProfileName.isNotEmpty()) getStore()?.containsKey(defaultProfileName)?.let { defaultProfileName } else null
    }

    override fun getProfileList(): ArrayList<CharSequence> =
        ArrayList<CharSequence>().also { ret -> getStore()?.keys?.forEach { ret.add(it) } }

    /**
     * Guarded by [lock] rather than `@Synchronized`, which is JVM only.
     *
     * Rewritten from a `var` mutated inside a closure: that could not smart cast, and it read the cache
     * and wrote it back in two separate steps. The behaviour is the same - first caller for a name
     * parses it, everyone after reuses that instance.
     */
    override fun getSpecificProfile(profileName: String): PureProfile? = lock.withLock {
        val store = getStore() ?: return@withLock null
        if (!store.containsKey(profileName)) return@withLock null
        cachedObjects[profileName]?.let { return@withLock it }

        val units = data.safeGetStringAllowNull("units", storeUnits())
        val profileObject = store.safeGetJSONObject(profileName, null) ?: return@withLock null
        pureProfileFromJson(profileObject, dateUtil, units)?.also { cachedObjects[profileName] = it }
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
