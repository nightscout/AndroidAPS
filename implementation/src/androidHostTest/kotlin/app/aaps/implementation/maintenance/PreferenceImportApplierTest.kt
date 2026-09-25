package app.aaps.implementation.maintenance

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.maintenance.Prefs
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.sharedPreferences.KeyValueStore
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanComposedKey
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.LongComposedKey
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.NonPreferenceKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.implementation.maintenance.migration.PreferenceMigrations
import app.aaps.implementation.sharedPreferences.PreferenceKeyResolverFactory
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyVararg
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * This class decides what an import is allowed to change, so a wrong answer here is a setting the
 * user did not ask for - or, for the pump rows, a pump that stops working.
 */
class PreferenceImportApplierTest {

    private val store = FakeStore()
    private val preferences = mock<Preferences>()

    private fun pumpPlugin(owned: List<NonPreferenceKey>): PluginBaseWithPreferences {
        val plugin = mock<PluginBaseWithPreferences>()
        whenever(plugin.pluginDescription).thenReturn(PluginDescription().mainType(PluginType.PUMP))
        whenever(plugin.ownPreferences).thenReturn(owned)
        return plugin
    }

    private fun sut(client: Boolean = false, plugins: List<PluginBase> = emptyList()): PreferenceImportApplier {
        val activePlugin = mock<ActivePlugin>()
        whenever(activePlugin.getPluginsList()).thenReturn(ArrayList(plugins))
        whenever(preferences.getAllKeys()).thenReturn(
            BooleanKey.entries + StringKey.entries + IntKey.entries + StringNonKey.entries + BooleanComposedKey.entries + DoubleKey.entries
        )
        val config = mock<Config>()
        whenever(config.AAPSCLIENT).thenReturn(client)
        return PreferenceImportApplier(
            store = store,
            preferences = preferences,
            resolverFactory = PreferenceKeyResolverFactory(preferences, activePlugin),
            config = config,
            aapsLogger = mock<AAPSLogger>(),
            dateUtil = mock<DateUtil>(),
            migrations = PreferenceMigrations(mock<AAPSLogger>(), config, mock<PersistenceLayer>(), mock<DateUtil>(), mock<ProfileUtil>())
        )
    }

    private fun prefs(vararg pairs: Pair<String, String>) = Prefs(mapOf(*pairs), mutableMapOf())

    @Test fun `a changed value is written and published once`() = runTest {
        store.putBoolean(BooleanKey.GeneralSimpleMode.key, true)

        val outcome = sut().apply(prefs(BooleanKey.GeneralSimpleMode.key to "false"), keepPumpSettings = false)

        assertThat(outcome.changed).isEqualTo(1)
        assertThat(store.getBoolean(BooleanKey.GeneralSimpleMode.key, true)).isFalse()
        verify(preferences).reloadFromStore()
    }

    @Test fun `an unchanged value is not written at all`() = runTest {
        store.putBoolean(BooleanKey.GeneralSimpleMode.key, false)

        val outcome = sut().apply(prefs(BooleanKey.GeneralSimpleMode.key to "false"), keepPumpSettings = false)

        assertThat(outcome.changed).isEqualTo(0)
        assertThat(outcome.unchanged).isEqualTo(1)
        // Nothing to publish, so nothing is published - and no synced key gets stamped for nothing.
        verify(preferences, never()).reloadFromStore()
    }

    /**
     * The one line that enforces "device state never arrives from a file". Old export files still
     * carry these names, because the flag changed after they were written.
     */
    @Test fun `a non-exportable key in the file is refused`() = runTest {
        val outcome = sut().apply(prefs(StringNonKey.ActivePumpSerialNumber.key to "999"), keepPumpSettings = false)

        assertThat(outcome.notExportable).isEqualTo(1)
        assertThat(outcome.changed).isEqualTo(0)
        assertThat(store.getString(StringNonKey.ActivePumpSerialNumber.key, "")).isEmpty()
    }

    @Test fun `a name this build does not know is left alone, not dropped`() = runTest {
        val outcome = sut().apply(prefs("key_from_a_newer_aaps" to "x"), keepPumpSettings = false)

        assertThat(outcome.unresolved).containsExactly("key_from_a_newer_aaps")
        assertThat(outcome.changed).isEqualTo(0)
    }

    @Test fun `a value that will not parse is skipped, not guessed`() = runTest {
        store.putInt(IntKey.ApsDynIsfAdjustmentFactor.key, 50)

        val outcome = sut().apply(prefs(IntKey.ApsDynIsfAdjustmentFactor.key to "not a number"), keepPumpSettings = false)

        assertThat(outcome.unreadable).containsExactly(IntKey.ApsDynIsfAdjustmentFactor.key)
        assertThat(store.getInt(IntKey.ApsDynIsfAdjustmentFactor.key, 0)).isEqualTo(50)
    }

    /**
     * Today's import guesses the type from the text: `if (value == "true") putBoolean else putString`.
     * A StringKey whose value happens to be "true" is then stored as a Boolean.
     */
    @Test fun `a string whose text looks like a boolean stays a string`() = runTest {
        val outcome = sut().apply(prefs(StringKey.GeneralUnits.key to "true"), keepPumpSettings = false)

        assertThat(outcome.changed).isEqualTo(1)
        assertThat(store.raw[StringKey.GeneralUnits.key]).isInstanceOf(String::class.java)
    }

    /**
     * An ABSENT key is never "unchanged", whatever the static default says.
     *
     * `Preferences.get` does not fall back to `key.defaultValue` for the eight
     * `calculatedDefaultValue` keys - it computes one from simple mode, the user's age and so on. So
     * a file value that happens to equal the static default used to be counted unchanged, nothing was
     * written, and the effective value stayed the COMPUTED default. For
     * `ns_allow_client_control` that means an import saying remote control is OFF left it ON.
     */
    @Test fun `a key absent from the store is always written, even if it equals the static default`() = runTest {
        val key = BooleanKey.GeneralSimpleMode          // any key; the point is that it is absent
        assertThat(store.raw).doesNotContainKey(key.key)

        val outcome = sut().apply(prefs(key.key to key.defaultValue.toString()), keepPumpSettings = false)

        assertThat(outcome.changed).isEqualTo(1)
        assertThat(outcome.unchanged).isEqualTo(0)
        assertThat(store.raw).containsKey(key.key)
    }

    /**
     * Android stores a double as a FLOAT, so 3.3 reads back as 3.299999952316284 and never equals the
     * "3.3" the file carries. Every double in every import counted as a change, which made the count
     * on the confirm screen meaningless.
     */
    @Test fun `a double that only differs at float precision is unchanged`() = runTest {
        val key = DoubleKey.ApsMaxBasal
        store.putDouble(key.key, 3.3.toFloat().toDouble())   // what Android would have stored

        val outcome = sut().apply(prefs(key.key to "3.3"), keepPumpSettings = false)

        assertThat(outcome.unchanged).isEqualTo(1)
        assertThat(outcome.changed).isEqualTo(0)
    }

    /**
     * The store is untyped and older builds wrote booleans as text, so reading by the key's declared
     * type can throw. That used to abort the entire import; now it writes, which also repairs the
     * wrongly-typed entry.
     */
    @Test fun `a stored value of the wrong native type is overwritten, not thrown on`() = runTest {
        val key = BooleanKey.GeneralSimpleMode
        store.raw[key.key] = "true"                     // a String where a Boolean belongs

        val outcome = sut().apply(prefs(key.key to "false"), keepPumpSettings = false)

        assertThat(outcome.changed).isEqualTo(1)
        assertThat(store.getBoolean(key.key, true)).isFalse()
    }

    // ---- the checkbox ----

    @Test fun `keeping pump settings skips pump-owned keys and counts them`() = runTest {
        val pumpKey = StringKey.GeneralUnits          // stand-in: whatever the pump plugin claims
        store.putString(pumpKey.key, "mgdl")

        val outcome = sut(plugins = listOf(pumpPlugin(listOf(pumpKey))))
            .apply(prefs(pumpKey.key to "mmol"), keepPumpSettings = true)

        assertThat(outcome.pumpSkipped).isEqualTo(1)
        assertThat(outcome.pumpWouldChange).isEqualTo(1)
        assertThat(store.getString(pumpKey.key, "")).isEqualTo("mgdl")
    }

    @Test fun `a full import writes the same pump key`() = runTest {
        val pumpKey = StringKey.GeneralUnits
        store.putString(pumpKey.key, "mgdl")

        val outcome = sut(plugins = listOf(pumpPlugin(listOf(pumpKey))))
            .apply(prefs(pumpKey.key to "mmol"), keepPumpSettings = false)

        assertThat(outcome.changed).isEqualTo(1)
        assertThat(store.getString(pumpKey.key, "")).isEqualTo("mmol")
    }

    /**
     * Pump SELECTION counts as pump configuration too. Restoring the file's selection while keeping
     * this phone's pump settings is the one combination that was explicitly ruled out: it leaves the
     * driver from the file pointed at configuration that belongs to a different pump.
     */
    @Test fun `keeping pump settings also skips the pump's ConfigBuilder entry`() = runTest {
        val pumpSelection = BooleanComposedKey.ConfigBuilderEnabled.composeKey("PUMP_SomePumpPlugin")
        val otherSelection = BooleanComposedKey.ConfigBuilderEnabled.composeKey("APS_OpenAPSSMBPlugin")

        val outcome = sut().apply(
            prefs(pumpSelection to "true", otherSelection to "true"),
            keepPumpSettings = true
        )

        assertThat(store.raw).doesNotContainKey(pumpSelection)
        assertThat(store.getBoolean(otherSelection, false)).isTrue()
    }

    /**
     * A master must mark an imported synced key as edited NOW, or a client's older copy wins it back.
     *
     * The batch writes below `Preferences`, so `onLocalSyncedWrite` never runs and the stamp keeps its
     * pre-import value. A client that edited the same key while offline then pushes with a newer
     * stamp, `ClientControlReceiver` sees `pushed.lastModified > ours`, accepts it, and the imported
     * value is gone - with the import log still saying it applied.
     */
    @Test fun `a master stamps the synced keys it imported`() = runTest {
        val synced = BooleanKey.GeneralSimpleMode      // SyncSpec(Cold, Bidirectional)
        store.putBoolean(synced.key, true)

        sut(client = false).apply(prefs(synced.key to "false"), keepPumpSettings = false)

        // anyVararg for the composed argument: each of these tests imports exactly one key, so "a stamp
        // was written" is unambiguous without matching the name through the vararg.
        verify(preferences).put(eq(LongComposedKey.SyncedPrefModified), anyVararg(), value = any())
    }

    @Test fun `an unchanged synced key is not stamped`() = runTest {
        val synced = BooleanKey.GeneralSimpleMode
        store.putBoolean(synced.key, false)

        sut(client = false).apply(prefs(synced.key to "false"), keepPumpSettings = false)

        verify(preferences, never()).put(eq(LongComposedKey.SyncedPrefModified), anyVararg(), value = any())
    }

    // ---- client ----

    @Test fun `a client never writes a synced key`() = runTest {
        // GeneralSimpleMode is Bidirectional. On a client it belongs to the master, and writing it
        // would be reverted by the master's next cold publish anyway.
        val outcome = sut(client = true).apply(prefs(BooleanKey.GeneralSimpleMode.key to "true"), keepPumpSettings = false)

        assertThat(outcome.syncedSkipped).isEqualTo(1)
        assertThat(store.raw).doesNotContainKey(BooleanKey.GeneralSimpleMode.key)
    }

    @Test fun `a master does write a synced key`() = runTest {
        val outcome = sut(client = false).apply(prefs(BooleanKey.GeneralSimpleMode.key to "false"), keepPumpSettings = false)

        assertThat(outcome.syncedSkipped).isEqualTo(0)
        assertThat(outcome.changed).isEqualTo(1)
    }

    // ---- preview ----

    @Test fun `preview reports the same counts and writes nothing`() = runTest {
        store.putBoolean(BooleanKey.GeneralSimpleMode.key, true)
        val file = prefs(BooleanKey.GeneralSimpleMode.key to "false")

        val previewed = sut().preview(file, keepPumpSettings = false)

        assertThat(previewed.changed).isEqualTo(1)
        assertThat(store.getBoolean(BooleanKey.GeneralSimpleMode.key, false)).isTrue()
        verify(preferences, never()).reloadFromStore()
    }

    /** A map-backed store, so these run on every platform without a device. */
    private class FakeStore : KeyValueStore {

        val raw = mutableMapOf<String, Any>()

        override fun getAll(): Map<String, *> = raw
        override fun clear() = raw.clear()
        override fun contains(key: String) = raw.containsKey(key)
        override fun remove(key: String) { raw.remove(key) }

        // Strict casts on purpose: Android's SPImpl has no try/catch either, so a stored entry whose
        // native type differs from the key's throws. A lenient `as?` here would quietly pass a test
        // for behaviour the real store does not have.
        override fun getString(key: String, defaultValue: String) = raw[key]?.let { it as String } ?: defaultValue
        override fun getStringOrNull(key: String, defaultValue: String?) = raw[key]?.let { it as String } ?: defaultValue
        override fun getBoolean(key: String, defaultValue: Boolean) = raw[key]?.let { it as Boolean } ?: defaultValue
        override fun getDouble(key: String, defaultValue: Double) = raw[key]?.let { it as Double } ?: defaultValue
        override fun getInt(key: String, defaultValue: Int) = raw[key]?.let { it as Int } ?: defaultValue
        override fun getLong(key: String, defaultValue: Long) = raw[key]?.let { it as Long } ?: defaultValue

        override fun putString(key: String, value: String) { raw[key] = value }
        override fun putBoolean(key: String, value: Boolean) { raw[key] = value }
        override fun putDouble(key: String, value: Double) { raw[key] = value }
        override fun putInt(key: String, value: Int) { raw[key] = value }
        override fun putLong(key: String, value: Long) { raw[key] = value }

        override fun incInt(key: String) = putInt(key, getInt(key, 0) + 1)
        override fun incLong(key: String) = putLong(key, getLong(key, 0L) + 1L)

        override fun edit(commit: Boolean, block: KeyValueStore.Editor.() -> Unit) {
            object : KeyValueStore.Editor {
                override fun clear() = raw.clear()
                override fun remove(key: String) { raw.remove(key) }
                override fun putBoolean(key: String, value: Boolean) { raw[key] = value }
                override fun putDouble(key: String, value: Double) { raw[key] = value }
                override fun putLong(key: String, value: Long) { raw[key] = value }
                override fun putInt(key: String, value: Int) { raw[key] = value }
                override fun putString(key: String, value: String) { raw[key] = value }
            }.block()
        }
    }
}
