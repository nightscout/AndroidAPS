package app.aaps.plugins.source.eversense

import android.content.Context
import android.content.SharedPreferences
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.eversense.storage.edit
import app.aaps.plugins.source.keys.EversenseRuntimeKey
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
    private val context: Context = mock()
    private val preferences: Preferences = mock()
    private val legacy: SharedPreferences = mock()
    private var stored: String? = null

    @BeforeEach fun setup() {
        whenever(context.getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE)).thenReturn(legacy)
        whenever(legacy.all).thenReturn(emptyMap())
        whenever(preferences.getIfExists(EversenseRuntimeKey.DeviceState)).thenAnswer { stored }
        whenever(preferences.get(EversenseRuntimeKey.DeviceState)).thenAnswer { stored ?: "{}" }
        doAnswer { stored = it.getArgument(1); null }.whenever(preferences).put(eq(EversenseRuntimeKey.DeviceState), any<String>())
    }

    @Test fun importsLegacyTypesAndDoesNotReadOldStateAgain() {
        whenever(legacy.all).thenReturn(mapOf("state" to "{\"serial\":\"test\"}", "expiry" to 9_000_000_000L, "days" to 14, "dismissed" to true))
        val store = EversenseStateStore(context, preferences)
        assertThat(store.getString("state", null)).isEqualTo("{\"serial\":\"test\"}")
        assertThat(store.getLong("expiry", 0)).isEqualTo(9_000_000_000L)
        assertThat(store.getInt("days", 0)).isEqualTo(14)
        assertThat(store.getBoolean("dismissed", false)).isTrue()
        EversenseStateStore(context, preferences)
        verify(context).getSharedPreferences("EversenseCGMManager", Context.MODE_PRIVATE)
        assertThat(EversenseRuntimeKey.DeviceState.exportable).isFalse()
    }

    @Test fun independentlyCreatedEditorsMergeAndNullRemovesAValue() {
        val store = EversenseStateStore(context, preferences)
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
        val store = EversenseStateStore(context, preferences)
        store.edit { putInt("old", 1) }
        store.edit { clear(); putBoolean("new", true) }
        assertThat(store.contains("old")).isFalse()
        assertThat(store.getBoolean("new", false)).isTrue()
    }
    @Test fun cloudQueueUsesSeparateNonExportablePreference() {
        val store = EversenseStateStore(context, preferences)
        val before = stored
        store.edit { putString(app.aaps.plugins.eversense.util.StorageKeys.CLOUD_QUEUE, "[]") }
        verify(preferences).put(EversenseRuntimeKey.CloudQueue, "[]")
        assertThat(stored).isEqualTo(before)
        assertThat(EversenseRuntimeKey.CloudQueue.exportable).isFalse()
    }

}
