package app.aaps.implementation.profile

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileStore
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.shared.tests.TestBase
import com.google.common.truth.Truth.assertThat
import dagger.Lazy
import org.json.JSONArray
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import javax.inject.Provider

class LocalProfileManagerImplTest : TestBase() {

    @Mock lateinit var rh: ResourceHelper
    @Mock lateinit var preferences: Preferences
    @Mock lateinit var sp: SP
    @Mock lateinit var profileFunction: ProfileFunction
    @Mock lateinit var profileUtil: ProfileUtil
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var hardLimits: HardLimits
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config
    @Mock lateinit var profileStoreProvider: Provider<ProfileStore>
    @Mock lateinit var profileStore: ProfileStore
    @Mock lateinit var notificationManager: NotificationManager

    private val storedValues = mutableMapOf<String, Any>()

    private val testIc = """[{"time":"00:00","timeAsSeconds":0,"value":10}]"""
    private val testIsf = """[{"time":"00:00","timeAsSeconds":0,"value":100}]"""
    private val testBasal = """[{"time":"00:00","timeAsSeconds":0,"value":1.0}]"""
    private val testTargetLow = """[{"time":"00:00","timeAsSeconds":0,"value":90}]"""
    private val testTargetHigh = """[{"time":"00:00","timeAsSeconds":0,"value":120}]"""

    @BeforeEach
    fun setup() {
        storedValues.clear()

        // Wire up preferences.put to store values
        whenever(preferences.put(any<StringNonKey>(), any<String>())).thenAnswer { invocation ->
            val key = invocation.getArgument<StringNonKey>(0)
            storedValues[key.key] = invocation.getArgument<String>(1)
        }
        whenever(preferences.put(any<LongNonKey>(), any<Long>())).thenAnswer { invocation ->
            val key = invocation.getArgument<LongNonKey>(0)
            storedValues[key.key] = invocation.getArgument<Long>(1)
        }

        // Default: new key returns empty array (no profiles)
        whenever(preferences.get(StringNonKey.LocalProfileData)).thenAnswer {
            storedValues.getOrDefault("local_profile_data", "[]") as String
        }

        whenever(profileStoreProvider.get()).thenReturn(profileStore)
        whenever(profileStore.with(any())).thenReturn(profileStore)

        // Default: empty raw SP, no legacy amount-of-profiles count
        whenever(sp.getAll()).thenReturn(emptyMap<String, Any>())
        whenever(sp.getInt(any<String>(), any())).thenReturn(0)
    }

    private fun createManager(): LocalProfileManagerImpl {
        val lazyProfileFunction = Lazy { profileFunction }
        return LocalProfileManagerImpl(
            aapsLogger = aapsLogger,
            rxBus = rxBus,
            rh = rh,
            preferences = preferences,
            sp = sp,
            profileFunction = lazyProfileFunction,
            profileUtil = profileUtil,
            activePlugin = activePlugin,
            hardLimits = hardLimits,
            dateUtil = dateUtil,
            config = config,
            profileStoreProvider = profileStoreProvider,
            notificationManager = notificationManager
        )
    }

    @Test
    fun `empty state produces no profiles`() {
        val manager = createManager()
        assertThat(manager.numOfProfiles).isEqualTo(0)
        assertThat(manager.profiles).isEmpty()
    }

    @Test
    fun `load from new JSON format`() {
        val json = JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("name", "TestProfile")
                put("mgdl", true)
                put("ic", JSONArray(testIc))
                put("isf", JSONArray(testIsf))
                put("basal", JSONArray(testBasal))
                put("targetLow", JSONArray(testTargetLow))
                put("targetHigh", JSONArray(testTargetHigh))
            })
        }
        whenever(preferences.get(StringNonKey.LocalProfileData)).thenReturn(json.toString())

        val manager = createManager()
        assertThat(manager.numOfProfiles).isEqualTo(1)
        assertThat(manager.profiles[0].name).isEqualTo("TestProfile")
        assertThat(manager.profiles[0].mgdl).isTrue()
        assertThat(manager.profiles[0].ic.getJSONObject(0).getInt("value")).isEqualTo(10)
        assertThat(manager.profiles[0].isf.getJSONObject(0).getInt("value")).isEqualTo(100)
        assertThat(manager.profiles[0].basal.getJSONObject(0).getDouble("value")).isEqualTo(1.0)
    }

    @Test
    fun `migrate from composed keys to single JSON`() {
        // Simulate old composed-key format via raw SP access.
        // Composed format: LocalProfile_<field>_<index> (field name first, then index)
        whenever(sp.getInt(eq("LocalProfile_profiles"), any())).thenReturn(2)

        whenever(sp.getString(eq("LocalProfile_name_0"), any())).thenReturn("Profile1")
        whenever(sp.getBoolean(eq("LocalProfile_mgdl_0"), any())).thenReturn(true)
        whenever(sp.getString(eq("LocalProfile_ic_0"), any())).thenReturn(testIc)
        whenever(sp.getString(eq("LocalProfile_isf_0"), any())).thenReturn(testIsf)
        whenever(sp.getString(eq("LocalProfile_basal_0"), any())).thenReturn(testBasal)
        whenever(sp.getString(eq("LocalProfile_targetlow_0"), any())).thenReturn(testTargetLow)
        whenever(sp.getString(eq("LocalProfile_targethigh_0"), any())).thenReturn(testTargetHigh)

        whenever(sp.getString(eq("LocalProfile_name_1"), any())).thenReturn("Profile2")
        whenever(sp.getBoolean(eq("LocalProfile_mgdl_1"), any())).thenReturn(false)
        whenever(sp.getString(eq("LocalProfile_ic_1"), any())).thenReturn(testIc)
        whenever(sp.getString(eq("LocalProfile_isf_1"), any())).thenReturn(testIsf)
        whenever(sp.getString(eq("LocalProfile_basal_1"), any())).thenReturn(testBasal)
        whenever(sp.getString(eq("LocalProfile_targetlow_1"), any())).thenReturn(testTargetLow)
        whenever(sp.getString(eq("LocalProfile_targethigh_1"), any())).thenReturn(testTargetHigh)

        val manager = createManager()

        // Verify profiles loaded correctly
        assertThat(manager.numOfProfiles).isEqualTo(2)
        assertThat(manager.profiles[0].name).isEqualTo("Profile1")
        assertThat(manager.profiles[0].mgdl).isTrue()
        assertThat(manager.profiles[1].name).isEqualTo("Profile2")
        assertThat(manager.profiles[1].mgdl).isFalse()

        // Verify new JSON was written
        assertThat(storedValues).containsKey("local_profile_data")
        val savedJson = JSONArray(storedValues["local_profile_data"] as String)
        assertThat(savedJson.length()).isEqualTo(2)
        assertThat(savedJson.getJSONObject(0).getString("name")).isEqualTo("Profile1")
        assertThat(savedJson.getJSONObject(1).getString("name")).isEqualTo("Profile2")
    }

    @Test
    fun `store and reload roundtrip`() {
        // Load with one profile from new format
        val json = JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("name", "RoundTrip")
                put("mgdl", false)
                put("ic", JSONArray(testIc))
                put("isf", JSONArray(testIsf))
                put("basal", JSONArray(testBasal))
                put("targetLow", JSONArray(testTargetLow))
                put("targetHigh", JSONArray(testTargetHigh))
            })
        }
        whenever(preferences.get(StringNonKey.LocalProfileData)).thenReturn(json.toString())

        val manager = createManager()
        assertThat(manager.numOfProfiles).isEqualTo(1)

        // Store settings
        manager.storeSettings(timestamp = 12345L)

        // Verify JSON was written back
        assertThat(storedValues).containsKey("local_profile_data")
        val savedJson = JSONArray(storedValues["local_profile_data"] as String)
        assertThat(savedJson.length()).isEqualTo(1)
        assertThat(savedJson.getJSONObject(0).getString("name")).isEqualTo("RoundTrip")
        assertThat(savedJson.getJSONObject(0).getBoolean("mgdl")).isFalse()

        // Verify timestamp was stored
        assertThat(storedValues["local_profile_last_change"]).isEqualTo(12345L)
    }

    @Test
    fun `multiple profiles with same name are deduplicated`() {
        val json = JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("name", "Same")
                put("mgdl", true)
                put("ic", JSONArray(testIc))
                put("isf", JSONArray(testIsf))
                put("basal", JSONArray(testBasal))
                put("targetLow", JSONArray(testTargetLow))
                put("targetHigh", JSONArray(testTargetHigh))
            })
            put(org.json.JSONObject().apply {
                put("name", "Same")
                put("mgdl", false)
                put("ic", JSONArray(testIc))
                put("isf", JSONArray(testIsf))
                put("basal", JSONArray(testBasal))
                put("targetLow", JSONArray(testTargetLow))
                put("targetHigh", JSONArray(testTargetHigh))
            })
        }
        whenever(preferences.get(StringNonKey.LocalProfileData)).thenReturn(json.toString())

        val manager = createManager()
        assertThat(manager.numOfProfiles).isEqualTo(1)
        assertThat(manager.profiles[0].name).isEqualTo("Same")
    }

    @Test
    fun `migrate from ancient raw SP format to single JSON`() {
        // Simulate ancient raw SP keys: LocalProfile_0_name, LocalProfile_0_isf, ..., LocalProfile_0_dia
        val removedKeys = mutableListOf<String>()
        val rawSpMap = mutableMapOf<String, Any>(
            "LocalProfile_0_name" to "OldProfile",
            "LocalProfile_0_mgdl" to true,
            "LocalProfile_0_ic" to testIc,
            "LocalProfile_0_isf" to testIsf,
            "LocalProfile_0_basal" to testBasal,
            "LocalProfile_0_targetlow" to testTargetLow,
            "LocalProfile_0_targethigh" to testTargetHigh,
            "LocalProfile_0_dia" to "6.0",
            "LocalProfile_profiles" to 1,
            // unrelated keys should be ignored
            "unrelated_key" to "whatever"
        )
        whenever(sp.getAll()).thenReturn(rawSpMap)
        whenever(sp.remove(any<String>())).thenAnswer { invocation ->
            removedKeys.add(invocation.getArgument(0))
            Unit
        }

        val manager = createManager()

        // Verify profile was loaded
        assertThat(manager.numOfProfiles).isEqualTo(1)
        assertThat(manager.profiles[0].name).isEqualTo("OldProfile")
        assertThat(manager.profiles[0].mgdl).isTrue()
        assertThat(manager.profiles[0].ic.getJSONObject(0).getInt("value")).isEqualTo(10)

        // Verify new JSON format was written
        assertThat(storedValues).containsKey("local_profile_data")
        val savedJson = JSONArray(storedValues["local_profile_data"] as String)
        assertThat(savedJson.length()).isEqualTo(1)
        assertThat(savedJson.getJSONObject(0).getString("name")).isEqualTo("OldProfile")

        // Verify legacy DIA map populated for ICfg backfill
        assertThat(manager.legacyProfileNameToDia).containsEntry("OldProfile", 6.0)

        // Verify all raw SP keys were removed (including _dia and _profiles)
        assertThat(removedKeys).containsAtLeast(
            "LocalProfile_0_name",
            "LocalProfile_0_mgdl",
            "LocalProfile_0_ic",
            "LocalProfile_0_isf",
            "LocalProfile_0_basal",
            "LocalProfile_0_targetlow",
            "LocalProfile_0_targethigh",
            "LocalProfile_0_dia",
            "LocalProfile_profiles"
        )
        // Unrelated keys must not be touched
        assertThat(removedKeys).doesNotContain("unrelated_key")
    }

    @Test
    fun `raw SP migration with multiple profiles`() {
        val rawSpMap = mutableMapOf<String, Any>(
            "LocalProfile_0_name" to "First",
            "LocalProfile_0_mgdl" to true,
            "LocalProfile_0_ic" to testIc,
            "LocalProfile_0_isf" to testIsf,
            "LocalProfile_0_basal" to testBasal,
            "LocalProfile_0_targetlow" to testTargetLow,
            "LocalProfile_0_targethigh" to testTargetHigh,
            "LocalProfile_0_dia" to "5.5",
            "LocalProfile_1_name" to "Second",
            "LocalProfile_1_mgdl" to false,
            "LocalProfile_1_ic" to testIc,
            "LocalProfile_1_isf" to testIsf,
            "LocalProfile_1_basal" to testBasal,
            "LocalProfile_1_targetlow" to testTargetLow,
            "LocalProfile_1_targethigh" to testTargetHigh,
            "LocalProfile_1_dia" to "7.0"
        )
        whenever(sp.getAll()).thenReturn(rawSpMap)

        val manager = createManager()

        assertThat(manager.numOfProfiles).isEqualTo(2)
        assertThat(manager.profiles[0].name).isEqualTo("First")
        assertThat(manager.profiles[0].mgdl).isTrue()
        assertThat(manager.profiles[1].name).isEqualTo("Second")
        assertThat(manager.profiles[1].mgdl).isFalse()

        assertThat(manager.legacyProfileNameToDia).containsEntry("First", 5.5)
        assertThat(manager.legacyProfileNameToDia).containsEntry("Second", 7.0)
    }

    @Test
    fun `raw SP migration ignores composed-format keys without crashing`() {
        // Composed-format keys (LocalProfile_name_0) use field_index order instead of index_field.
        // They must NOT match any endsWith branch in migrateFromRawSp() — otherwise split("_")[1]
        // would throw NumberFormatException on a field name.
        // These keys belong to the second-tier migration path (migrateFromComposedKeys),
        // but they physically live in the same SharedPreferences backend.
        val rawSpMap = mutableMapOf<String, Any>(
            // Ancient format (should be migrated)
            "LocalProfile_0_name" to "Ancient",
            "LocalProfile_0_mgdl" to true,
            "LocalProfile_0_ic" to testIc,
            "LocalProfile_0_isf" to testIsf,
            "LocalProfile_0_basal" to testBasal,
            "LocalProfile_0_targetlow" to testTargetLow,
            "LocalProfile_0_targethigh" to testTargetHigh,
            // Composed format (must be ignored — would crash if parsed with split[1].toInt())
            "LocalProfile_name_5" to "Composed",
            "LocalProfile_isf_5" to testIsf,
            "LocalProfile_mgdl_5" to false
        )
        whenever(sp.getAll()).thenReturn(rawSpMap)

        // Must not crash and must correctly migrate only the ancient profile
        val manager = createManager()

        assertThat(manager.numOfProfiles).isEqualTo(1)
        assertThat(manager.profiles[0].name).isEqualTo("Ancient")
    }

    @Test
    fun `raw SP migration prefers existing JSON format over raw SP`() {
        // Both formats present — new JSON must win, raw SP must not be touched
        val json = JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("name", "NewFormat")
                put("mgdl", true)
                put("ic", JSONArray(testIc))
                put("isf", JSONArray(testIsf))
                put("basal", JSONArray(testBasal))
                put("targetLow", JSONArray(testTargetLow))
                put("targetHigh", JSONArray(testTargetHigh))
            })
        }
        whenever(preferences.get(StringNonKey.LocalProfileData)).thenReturn(json.toString())
        whenever(sp.getAll()).thenReturn(
            mapOf<String, Any>(
                "LocalProfile_0_name" to "OldFormat",
                "LocalProfile_0_dia" to "6.0"
            )
        )

        val manager = createManager()

        assertThat(manager.numOfProfiles).isEqualTo(1)
        assertThat(manager.profiles[0].name).isEqualTo("NewFormat")
        // Legacy map must be empty — raw SP migration should not have run
        assertThat(manager.legacyProfileNameToDia).isEmpty()
    }
}
