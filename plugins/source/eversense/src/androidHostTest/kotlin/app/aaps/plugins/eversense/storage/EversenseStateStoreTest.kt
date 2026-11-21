package app.aaps.plugins.eversense.storage

import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.keys.EversenseRuntimeKey
import app.aaps.plugins.eversense.util.StorageKeys
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class EversenseStateStoreTest {

    private val preferences: Preferences = mock()
    private var stored: String? = null

    /** The old SharedPreferences "EversenseCGMManager", and how often the store read it. */
    private var legacyValues: Map<String, Any?> = emptyMap()
    private var legacyReads = 0
    private val legacy = EversenseLegacyStore { legacyReads++; legacyValues }

    @BeforeEach fun setup() {
        whenever(preferences.getIfExists(EversenseRuntimeKey.DeviceState)).thenAnswer { stored }
        whenever(preferences.get(EversenseRuntimeKey.DeviceState)).thenAnswer { stored ?: "{}" }
        doAnswer { stored = it.getArgument(1); null }.whenever(preferences).put(eq(EversenseRuntimeKey.DeviceState), any<String>())
    }

    @Test fun importsLegacyTypesAndDoesNotReadOldStateAgain() {
        legacyValues = mapOf("state" to "{\"serial\":\"test\"}", "expiry" to 9_000_000_000L, "days" to 14, "dismissed" to true)
        val store = EversenseStateStore(legacy, preferences)
        assertThat(store.getString("state", null)).isEqualTo("{\"serial\":\"test\"}")
        assertThat(store.getLong("expiry", 0)).isEqualTo(9_000_000_000L)
        assertThat(store.getInt("days", 0)).isEqualTo(14)
        assertThat(store.getBoolean("dismissed", false)).isTrue()
        EversenseStateStore(legacy, preferences)
        assertThat(legacyReads).isEqualTo(1)
        assertThat(EversenseRuntimeKey.DeviceState.exportable).isFalse()
    }

    /** Where no old store exists (iOS, a new install), the import stores an empty state and reads nothing more. */
    @Test fun anEmptyLegacyStoreStartsWithAnEmptyState() {
        val store = EversenseStateStore(legacy, preferences)
        assertThat(stored).isEqualTo("{}")
        assertThat(store.contains("state")).isFalse()
        assertThat(store.getString("state", "fallback")).isEqualTo("fallback")
        EversenseStateStore(legacy, preferences)
        assertThat(legacyReads).isEqualTo(1)
    }

    @Test fun independentlyCreatedEditorsMergeAndNullRemovesAValue() {
        val store = EversenseStateStore(legacy, preferences)
        val first = store.edit().putString("device", "test")
        val second = store.edit().putLong("sequence", 5L)
        first.commit()
        second.apply()
        assertThat(store.getString("device", null)).isEqualTo("test")
        assertThat(store.getLong("sequence", 0L)).isEqualTo(5L)
        store.edit(commit = true) { putString("device", null) }
        assertThat(store.contains("device")).isFalse()
        assertThat(store.getString("device", "fallback")).isEqualTo("fallback")
        assertThat(store.getLong("sequence", 0L)).isEqualTo(5L)
    }

    @Test fun clearKeepsOnlyValuesFromTheCurrentEdit() {
        val store = EversenseStateStore(legacy, preferences)
        store.edit { putInt("old", 1) }
        store.edit { clear(); putBoolean("new", true) }
        assertThat(store.contains("old")).isFalse()
        assertThat(store.getBoolean("new", false)).isTrue()
    }

    @Test fun cloudQueueUsesSeparateNonExportablePreference() {
        val store = EversenseStateStore(legacy, preferences)
        val before = stored
        store.edit { putString(StorageKeys.CLOUD_QUEUE, "[]") }
        verify(preferences).put(EversenseRuntimeKey.CloudQueue, "[]")
        assertThat(stored).isEqualTo(before)
        assertThat(EversenseRuntimeKey.CloudQueue.exportable).isFalse()
    }
}
