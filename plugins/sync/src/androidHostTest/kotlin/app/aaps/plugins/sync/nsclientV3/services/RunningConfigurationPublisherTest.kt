package app.aaps.plugins.sync.nsclientV3.services

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * What the master writes into the cold running-config doc.
 *
 * The client-control switch used to be rewritten here (published as off while the WebSocket was
 * off). Hardened mode keeps the WebSocket on while the switch is on, so the switch now travels as it
 * is, like every other cold key, and the client applies the same rule to it as the master.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RunningConfigurationPublisherTest {

    private val nsAndroidClient = mock<NSAndroidClient>()
    private val preferences = mock<Preferences>()
    private val runningConfiguration = mock<RunningConfiguration>()
    private val authorizedRepository = mock<AuthorizedClientsRepository>()

    private val syncedPrefs = buildJsonObject {
        put(BooleanKey.HardenedMode.key, "true")
        put(BooleanKey.GeneralSimpleMode.key, "false")
    }

    private fun client(id: String, state: ClientState, role: ClientRole? = null) =
        AuthorizedClient(clientId = id, name = id, encryptedSecret = "enc", state = state, createdAt = 1_000L, role = role?.name)

    private fun publisher(): RunningConfigurationPublisher {
        val plugin = mock<NSClientV3Plugin>()
        whenever(plugin.nsAndroidClient).thenReturn(nsAndroidClient)
        whenever(plugin.wsConnectedFlow).thenReturn(MutableStateFlow(false))
        val keys = mock<RunningConfigurationKeys>()
        whenever(keys.observableKeys()).thenReturn(emptyList())
        whenever(keys.hotKeys()).thenReturn(emptyList())
        val rxBus = mock<RxBus>()
        whenever(rxBus.toFlow(EventConfigBuilderChange::class)).thenReturn(emptyFlow())
        val config = mock<Config>()
        whenever(config.APS).thenReturn(true)
        val dateUtil = mock<DateUtil>()
        whenever(dateUtil.now()).thenReturn(10_000L)
        return RunningConfigurationPublisher(
            runningConfiguration, keys, { plugin }, preferences, rxBus, mock<AAPSLogger>(), mock<NSClientRepository>(), config, dateUtil, authorizedRepository
        )
    }

    @Test
    fun `the switch travels as stored and the roster lists only active clients`() = runTest {
        // WebSocket off: the start-time publish path, the one that used to rewrite the switch to off.
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(false)
        whenever(runningConfiguration.configuration()).thenReturn(buildJsonObject { put("syncedPrefs", syncedPrefs) })
        whenever(runningConfiguration.activeSceneConfiguration()).thenReturn(JsonObject(emptyMap()))
        whenever(authorizedRepository.observe()).thenReturn(emptyFlow())
        whenever(authorizedRepository.current(any())).thenReturn(
            listOf(client("active", ClientState.Active), client("meals", ClientState.Active, ClientRole.MealAssistant), client("pending", ClientState.Pending))
        )
        whenever(nsAndroidClient.updateSettings(any(), any()))
            .thenReturn(CreateUpdateResponse(response = 200, identifier = null, isDeduplication = false, deduplicatedIdentifier = null, lastModified = null, errorResponse = null))
        val sut = publisher()

        sut.start(backgroundScope)
        // runCurrent, not advanceUntilIdle: the latter does not wait for backgroundScope work.
        runCurrent()
        sut.stop()

        val doc = argumentCaptor<JsonObject>()
        verify(nsAndroidClient).updateSettings(eq(SettingsIdentifiers.COLD), doc.capture())
        val runningConfig = doc.firstValue["runningConfig"]!!.jsonObject
        assertThat(runningConfig["syncedPrefs"]).isEqualTo(syncedPrefs)
        val roster = runningConfig["authorizedClients"]!!.jsonObject
        assertThat(roster["clientIds"]!!.jsonArray).isEqualTo(JsonArray(listOf(JsonPrimitive("active"), JsonPrimitive("meals"))))
        // Each active client's role travels too (none stored = Admin); a pending one is not listed.
        assertThat(roster["roles"]).isEqualTo(buildJsonObject { put("active", "Admin"); put("meals", "MealAssistant") })
    }
}
