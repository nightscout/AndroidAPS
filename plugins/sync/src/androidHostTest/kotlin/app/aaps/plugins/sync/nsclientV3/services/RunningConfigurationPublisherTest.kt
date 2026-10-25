package app.aaps.plugins.sync.nsclientV3.services

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.configuration.RunningConfigurationKeys
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.protection.SecureEncrypt
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventConfigBuilderChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.core.keys.interfaces.ClientRole
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.nssdk.interfaces.NSAndroidClient
import app.aaps.core.nssdk.interfaces.RunningConfiguration
import app.aaps.core.nssdk.localmodel.clientcontrol.AuthorizedClient
import app.aaps.core.nssdk.localmodel.clientcontrol.ClientState
import app.aaps.core.nssdk.localmodel.treatment.CreateUpdateResponse
import app.aaps.core.nssdk.utils.MasterDocSigning
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.SettingsIdentifiers
import app.aaps.plugins.sync.nsclientV3.clientcontrol.AuthorizedClientsRepository
import app.aaps.plugins.sync.nsclientV3.clientcontrol.MasterDocVerifier
import app.aaps.plugins.sync.nsclientV3.clientcontrol.MasterSigningKey
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
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

    // The master's real signing key, over preferences of its own and a key store that just marks what it wraps.
    private val signingStrings = mutableMapOf<String, String>()
    private val signingLongs = mutableMapOf<String, Long>()
    private val signingKey: MasterSigningKey by lazy {
        val prefs = mock<Preferences>()
        whenever(prefs.get(any<StringNonKey>())).thenAnswer { signingStrings[(it.arguments[0] as StringNonKey).key] ?: "" }
        whenever(prefs.put(any<StringNonKey>(), any<String>())).thenAnswer { signingStrings[(it.arguments[0] as StringNonKey).key] = it.arguments[1] as String; Unit }
        whenever(prefs.get(any<LongNonKey>())).thenAnswer { signingLongs[(it.arguments[0] as LongNonKey).key] ?: 0L }
        whenever(prefs.put(any<LongNonKey>(), any<Long>())).thenAnswer { signingLongs[(it.arguments[0] as LongNonKey).key] = it.arguments[1] as Long; Unit }
        val keyStore = object : SecureEncrypt {
            override fun encrypt(plaintextSecret: String, keystoreAlias: String): String = "ENC:$plaintextSecret"
            override fun decrypt(encryptedSecret: String): String = encryptedSecret.removePrefix("ENC:")
            override fun isValidDataString(data: String?): Boolean = data?.startsWith("ENC:") == true
            override fun deleteKey(keystoreAlias: String) {}
        }
        val clock = mock<DateUtil>()
        whenever(clock.now()).thenReturn(10_000L)
        MasterSigningKey(prefs, keyStore, clock, mock<AAPSLogger>())
    }

    /** The configuration in a published doc, after checking that the master signed it. */
    private suspend fun openSigned(identifier: String, doc: JsonObject): JsonObject {
        val text = doc[MasterDocVerifier.SIGNED]!!.jsonPrimitive.content
        val signature = doc[MasterDocVerifier.SIGNATURE]!!.jsonPrimitive.content
        assertThat(MasterDocSigning.verify(signingKey.publicKey()!!, identifier, text, signature)).isTrue()
        return Json.parseToJsonElement(text).jsonObject[MasterDocVerifier.RUNNING_CONFIG]!!.jsonObject
    }

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
            runningConfiguration, keys, { plugin }, preferences, rxBus, mock<AAPSLogger>(), mock<NSClientRepository>(), config, dateUtil, authorizedRepository, signingKey
        )
    }

    @Test
    fun `the switch travels as stored and the roster lists only active clients`() = runTest {
        // WebSocket off: the start-time publish path, the one that used to rewrite the switch to off.
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(false)
        whenever(runningConfiguration.configuration()).thenReturn(buildJsonObject { put("syncedPrefs", syncedPrefs) })
        whenever(runningConfiguration.hotConfiguration()).thenReturn(JsonObject(emptyMap()))
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
        val runningConfig = openSigned(SettingsIdentifiers.COLD, doc.firstValue)
        assertThat(runningConfig["syncedPrefs"]).isEqualTo(syncedPrefs)
        val roster = runningConfig["authorizedClients"]!!.jsonObject
        assertThat(roster["clientIds"]!!.jsonArray).isEqualTo(JsonArray(listOf(JsonPrimitive("active"), JsonPrimitive("meals"))))
        // Each active client's role travels too (none stored = Admin); a pending one is not listed.
        assertThat(roster["roles"]).isEqualTo(buildJsonObject { put("active", "Admin"); put("meals", "MealAssistant") })
    }

    /**
     * The hot doc is signed too, for its own identifier, and each doc is signed later than the one before, also with the
     * clock standing still: a client refuses an older one as a replay.
     */
    @Test
    fun `both docs are signed and each one later than the one before`() = runTest {
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(false)
        whenever(runningConfiguration.configuration()).thenReturn(buildJsonObject { put("syncedPrefs", syncedPrefs) })
        val scene = buildJsonObject { put("sceneId", "scene-1") }
        whenever(runningConfiguration.hotConfiguration()).thenReturn(buildJsonObject { put("activeScene", scene) })
        whenever(authorizedRepository.observe()).thenReturn(emptyFlow())
        whenever(authorizedRepository.current(any())).thenReturn(emptyList())
        whenever(nsAndroidClient.updateSettings(any(), any())).thenReturn(response(200))
        val sut = publisher()

        sut.start(backgroundScope)
        runCurrent()
        sut.stop()

        val cold = argumentCaptor<JsonObject>()
        val hot = argumentCaptor<JsonObject>()
        verify(nsAndroidClient).updateSettings(eq(SettingsIdentifiers.COLD), cold.capture())
        verify(nsAndroidClient).updateSettings(eq(SettingsIdentifiers.STATE), hot.capture())
        assertThat(openSigned(SettingsIdentifiers.STATE, hot.firstValue)["activeScene"]).isEqualTo(scene)
        val signedAt = { doc: JsonObject -> Json.parseToJsonElement(doc[MasterDocVerifier.SIGNED]!!.jsonPrimitive.content).jsonObject[MasterDocVerifier.SIGNED_AT]!!.jsonPrimitive.long }
        assertThat(signedAt(hot.firstValue)).isGreaterThan(signedAt(cold.firstValue))
    }

    private fun response(code: Int) =
        CreateUpdateResponse(response = code, identifier = null, isDeduplication = false, deduplicatedIdentifier = null, lastModified = null, errorResponse = null)

    /** WebSocket off, so start() publishes at once. */
    private fun stubPollMode() {
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(false)
        whenever(runningConfiguration.hotConfiguration()).thenReturn(JsonObject(emptyMap()))
        whenever(authorizedRepository.observe()).thenReturn(emptyFlow())
    }

    @Test
    fun `a cold doc skipped while the pump is not ready is published by the retry`() = runTest {
        stubPollMode()
        whenever(authorizedRepository.current(any())).thenReturn(listOf(client("new", ClientState.Active)))
        // Not initialized at start (empty), ready by the retry.
        whenever(runningConfiguration.configuration()).thenReturn(JsonObject(emptyMap()), buildJsonObject { put("syncedPrefs", syncedPrefs) })
        whenever(nsAndroidClient.updateSettings(any(), any())).thenReturn(response(200))
        val sut = publisher()

        sut.start(backgroundScope)
        runCurrent()
        verify(nsAndroidClient, never()).updateSettings(eq(SettingsIdentifiers.COLD), any())

        // The retry waits 30 s, then goes through the 5 s cold debounce.
        advanceTimeBy(35_001L)
        verify(nsAndroidClient, times(1)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        sut.stop()
    }

    /** Two failures close together (start and WS connect) share one retry: the first one still comes after 30 s. */
    @Test
    fun `two failures close together keep the first retry`() = runTest {
        stubPollMode()
        whenever(authorizedRepository.current(any())).thenReturn(listOf(client("new", ClientState.Active)))
        whenever(runningConfiguration.configuration()).thenReturn(buildJsonObject { put("syncedPrefs", syncedPrefs) })
        whenever(nsAndroidClient.updateSettings(eq(SettingsIdentifiers.COLD), any())).thenReturn(response(500), response(500), response(200))
        whenever(nsAndroidClient.updateSettings(eq(SettingsIdentifiers.STATE), any())).thenReturn(response(200))
        val sut = publisher()

        sut.start(backgroundScope)
        runCurrent()
        // A second failure before the retry, through the cold trigger.
        sut.requestColdRepublish()
        advanceTimeBy(5_001L)
        verify(nsAndroidClient, times(2)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        // The retry set by the first failure: 30 s after the start, plus the debounce.
        advanceTimeBy(30_000L)
        verify(nsAndroidClient, times(3)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        sut.stop()
    }

    @Test
    fun `a failed upload is tried again, each time waiting twice as long, until it goes through`() = runTest {
        stubPollMode()
        whenever(authorizedRepository.current(any())).thenReturn(listOf(client("new", ClientState.Active)))
        whenever(runningConfiguration.configuration()).thenReturn(buildJsonObject { put("syncedPrefs", syncedPrefs) })
        whenever(nsAndroidClient.updateSettings(eq(SettingsIdentifiers.COLD), any())).thenReturn(response(500), response(500), response(200))
        whenever(nsAndroidClient.updateSettings(eq(SettingsIdentifiers.STATE), any())).thenReturn(response(200))
        val sut = publisher()

        sut.start(backgroundScope)
        runCurrent()
        verify(nsAndroidClient, times(1)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        // Second try after 30 s + debounce.
        advanceTimeBy(35_001L)
        verify(nsAndroidClient, times(2)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        // The third waits 60 s: not yet 4 s before it, then it goes through.
        advanceTimeBy(61_000L)
        verify(nsAndroidClient, times(2)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        advanceTimeBy(4_000L)
        verify(nsAndroidClient, times(3)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        // Published: nothing waits any more.
        advanceTimeBy(20L * 60L * 1000L)
        verify(nsAndroidClient, times(3)).updateSettings(eq(SettingsIdentifiers.COLD), any())
        sut.stop()
    }
}
