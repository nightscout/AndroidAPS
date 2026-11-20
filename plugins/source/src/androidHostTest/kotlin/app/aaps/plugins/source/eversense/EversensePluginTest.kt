package app.aaps.plugins.source.eversense

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.keys.PreferenceType
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.keys.LongNonKey
import app.aaps.plugins.eversense.EversenseCGMPlugin
import app.aaps.plugins.eversense.enums.EversenseType
import app.aaps.plugins.eversense.enums.EversenseTrendArrow
import app.aaps.plugins.eversense.models.EversenseCGMResult
import app.aaps.plugins.eversense.models.EversenseState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.whenever
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.source.EversensePlugin
import app.aaps.plugins.source.keys.EversenseBooleanKey
import app.aaps.plugins.source.keys.EversenseRuntimeKey
import app.aaps.plugins.source.keys.EversenseIntentKey
import app.aaps.plugins.source.keys.EversenseStringKey as CredentialsKey
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class EversensePluginTest {
    @Test fun registersCredentialsAndCloudSettingsAndProvidesDeviceActions() {
        val preferences: Preferences = mock()
        val plugin = EversensePlugin(mock(), mock(), mock(), preferences, mock(), mock(), mock(), mock())
        verify(preferences).registerPreferences(CredentialsKey.entries + EversenseBooleanKey.entries + EversenseIntentKey.entries + EversenseStringKey.entries + EversenseRuntimeKey.entries)
        assertThat(plugin.pluginDescription.mainType).isEqualTo(PluginType.BGSOURCE)
        assertThat(plugin.pluginDescription.composeContentProvider).isNotNull()
        assertThat(plugin.pluginDescription.defaultPlugin).isFalse()
        val screen = plugin.getPreferenceScreenContent()
        assertThat(screen.items).contains(EversenseBooleanKey.EversenseCloudUploadEnabled)
        assertThat(screen.items).contains(EversenseBooleanKey.EversenseDiagnosticLogging)
        assertThat(EversenseBooleanKey.EversenseDiagnosticLogging.defaultValue).isFalse()
        assertThat(EversenseBooleanKey.EversenseDiagnosticLogging.exportable).isFalse()
        assertThat(EversenseBooleanKey.entries.all { it.preferenceType == PreferenceType.SWITCH }).isTrue()
        val credentials = screen.items.filterIsInstance<PreferenceSubScreenDef>().single()
        assertThat(credentials.items).containsAtLeast(CredentialsKey.EversenseUsername, CredentialsKey.EversensePassword)
    }
    @Test fun successfulCalibrationUpdatesCountdownAndRequestsSync() = runBlocking {
        val preferences: Preferences = mock()
        val backend: EversenseCGMPlugin = mock()
        whenever(backend.isConnected()).thenReturn(true)
        whenever(backend.sendCalibration(any(), any())).thenReturn(true)
        val plugin = EversensePlugin(mock(), mock(), mock(), preferences, mock(), mock(), { backend }, mock())
        assertThat(plugin.calibrate(100)).isTrue()
        verify(backend).sendCalibration(eq(100), any())
        verify(backend).triggerFullSync(force = true)
        verify(preferences).put(eq(LongNonKey.EversenseLastCalibrationSubmittedAt), any())
    }

    @Test fun failedCalibrationDoesNotStartCountdown() = runBlocking {
        val preferences: Preferences = mock()
        val backend: EversenseCGMPlugin = mock()
        whenever(backend.isConnected()).thenReturn(true)
        whenever(backend.sendCalibration(any(), any())).thenReturn(false)
        val plugin = EversensePlugin(mock(), mock(), mock(), preferences, mock(), mock(), { backend }, mock())
        assertThat(plugin.calibrate(100)).isFalse()
        verify(preferences, never()).put(eq(LongNonKey.EversenseLastCalibrationSubmittedAt), any())
        verify(backend, never()).triggerFullSync(any())
    }

    @Test fun readingsKeepValuesTimestampsTrendsAndModelWhenPassedToPersistence() = runBlocking {
        for ((type, sensor) in listOf(EversenseType.EVERSENSE_E3 to SourceSensor.EVERSENSE_E3, EversenseType.EVERSENSE_365 to SourceSensor.EVERSENSE_365)) {
            val backend: EversenseCGMPlugin = mock()
            whenever(backend.getCurrentState()).thenReturn(EversenseState())
            val persistence: PersistenceLayer = mock()
            val received = CompletableDeferred<List<GV>>()
            whenever(persistence.insertCgmSourceData(any(), any(), any(), anyOrNull())).thenAnswer {
                received.complete(it.getArgument(1))
                PersistenceLayer.TransactionResult<GV>()
            }
            val plugin = EversensePlugin(mock(), mock(), mock(), mock(), mock(), mock(), { backend }, persistence)
            plugin.onCGMRead(type, listOf(EversenseCGMResult(123, 1_700_000_000_000L, EversenseTrendArrow.FLAT)))
            val values = withTimeout(5_000) { received.await() }
            assertThat(values.single().value).isEqualTo(123.0)
            assertThat(values.single().timestamp).isEqualTo(1_700_000_000_000L)
            assertThat(values.single().sourceSensor).isEqualTo(sensor)
            assertThat(values.single().trendArrow).isEqualTo(TrendArrow.FLAT)
            verify(persistence).insertCgmSourceData(Sources.Eversense, values, emptyList(), null)
            plugin.onStop()
        }
    }

    @Test fun settingsImportHooksDoNotRewindLiveDeviceState() {
        val preferences: Preferences = mock()
        val plugin = EversensePlugin(mock(), mock(), mock(), preferences, mock(), mock(), { error("Bluetooth must stay closed") }, mock())
        plugin.beforeImport()
        plugin.afterImport()
        verify(preferences, never()).put(eq(EversenseRuntimeKey.DeviceState), any<String>())
    }

    @Test fun signingOutClearsTypedCredentialsAndStopsBackendSession() {
        val preferences: Preferences = mock()
        val backend: EversenseCGMPlugin = mock()
        val plugin = EversensePlugin(mock(), mock(), mock(), preferences, mock(), mock(), { backend }, mock())
        val screen = plugin.getPreferenceScreenContent()
        assertThat(screen.items.filterIsInstance<app.aaps.core.keys.interfaces.IntentPreferenceKey>()
            .any { it.key == EversenseIntentKey.EversenseSignOut.key }).isFalse()
        val action = screen.items.filterIsInstance<PreferenceSubScreenDef>().single().items
            .filterIsInstance<app.aaps.core.keys.interfaces.IntentPreferenceKey>()
            .single { it.key == EversenseIntentKey.EversenseSignOut.key }
        assertThat(action.confirmationMessage).isNotNull()
        assertThat(CredentialsKey.EversensePassword.requiresPasswordConfirmation).isFalse()
        action.onClick?.invoke()
        verify(preferences).put(CredentialsKey.EversenseUsername, "")
        verify(preferences).put(CredentialsKey.EversensePassword, "")
        verify(backend).clearCredentials()
    }

    @Test fun stoppingSourceAlsoStopsBackendAndRemovesWatcher() = runBlocking {
        val backend: EversenseCGMPlugin = mock()
        val plugin = EversensePlugin(mock(), mock(), mock(), mock(), mock(), mock(), { backend }, mock())
        plugin.onStop()
        verify(backend).removeWatcher(plugin)
        verify(backend).stop()
    }

    @Test fun localGlucosePersistenceContinuesWhenCloudQueueFails() = runBlocking {
        val backend: EversenseCGMPlugin = mock()
        val preferences: Preferences = mock()
        whenever(backend.getCurrentState()).thenReturn(EversenseState())
        whenever(preferences.get(EversenseBooleanKey.EversenseCloudUploadEnabled)).thenReturn(true)
        val cloudFailed = CompletableDeferred<Unit>()
        whenever(backend.cloudStore()).thenAnswer { cloudFailed.complete(Unit); throw IllegalStateException("cloud unavailable") }
        val persistence: PersistenceLayer = mock()
        val received = kotlinx.coroutines.channels.Channel<List<GV>>(2)
        whenever(persistence.insertCgmSourceData(any(), any(), any(), anyOrNull())).thenAnswer {
            received.trySend(it.getArgument(1))
            PersistenceLayer.TransactionResult<GV>()
        }
        val plugin = EversensePlugin(mock(), mock(), mock(), preferences, mock(), mock(), { backend }, persistence)
        try {
            plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(123, 1_800_000_000_000L, EversenseTrendArrow.FLAT)))
            assertThat(withTimeout(5_000) { received.receive() }.single().value).isEqualTo(123.0)
            withTimeout(5_000) { cloudFailed.await() }
            plugin.onCGMRead(EversenseType.EVERSENSE_365, listOf(EversenseCGMResult(124, 1_800_000_300_000L, EversenseTrendArrow.FLAT)))
            assertThat(withTimeout(5_000) { received.receive() }.single().value).isEqualTo(124.0)
            verify(backend, never()).forceReconnect()
            verify(backend, never()).disconnect()
        } finally { plugin.onStop() }
    }

}
