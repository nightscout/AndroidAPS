package app.aaps.plugins.sync.garmin

import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SC
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.keys.BooleanNonKey
import app.aaps.core.keys.IntNonKey
import app.aaps.core.keys.StringNonKey
import app.aaps.plugins.sync.SyncStringsValues
import app.aaps.plugins.sync.garmin.keys.GarminBooleanKey
import app.aaps.plugins.sync.garmin.keys.GarminIntKey
import app.aaps.plugins.sync.garmin.keys.GarminIntNonKey
import app.aaps.plugins.sync.garmin.keys.GarminStringKey
import app.aaps.shared.tests.TestBaseWithProfile
import app.aaps.shared.tests.generatedTextResolver
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.atMost
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketAddress
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.locks.Condition
import kotlin.ranges.LongProgression.Companion.fromClosedRange

class GarminPluginTest : TestBaseWithProfile() {

    private lateinit var gp: GarminPlugin

    @Mock private lateinit var loopHub: LoopHub
    @Mock private lateinit var persistenceLayer: PersistenceLayer
    private val clock = Clock.fixed(Instant.ofEpochMilli(10_000), ZoneId.of("UTC"))

    @BeforeEach
    fun setup() {
        gp = GarminPlugin(aapsLogger, rh, preferences, context, loopHub, persistenceLayer, config, notificationManager)
        gp.clock = clock
        whenever(loopHub.currentProfileName).thenReturn("Default")
        whenever(preferences.get(GarminIntKey.LocalHttpPort)).thenReturn(28890)
        whenever(preferences.get(any<IntNonKey>())).thenAnswer { i -> 0 }
        whenever(preferences.get(any<BooleanNonKey>())).thenAnswer { i -> false }
        whenever(preferences.get(any<StringNonKey>())).thenAnswer { i -> "" }
    }

    @AfterEach
    fun verifyNoFurtherInteractions() {
        verify(loopHub, atMost(2)).currentProfileName
        verify(loopHub, atMost(3)).insulinOnboard
        verify(loopHub, atMost(3)).insulinBasalOnboard
        verify(loopHub, atMost(3)).temporaryBasal
        verify(loopHub, atMost(3)).carbsOnboard
        verify(loopHub, atMost(3)).lowGlucoseMark
        verify(loopHub, atMost(3)).highGlucoseMark
        verifyNoMoreInteractions(loopHub)
    }

    private val getGlucoseValuesFrom = clock.instant()
        .minus(2, ChronoUnit.HOURS)
        .minus(9, ChronoUnit.MINUTES)

    private fun createUri(params: Map<String, Any>): URI {
        return URI("http://foo?" + params.entries.joinToString(separator = "&") { (k, v) ->
            "$k=$v"
        })
    }

    private fun createHeartRate(@Suppress("SameParameterValue") heartRate: Int) = mapOf<String, Any>(
        "hr" to heartRate,
        "hrStart" to 1001L,
        "hrEnd" to 2001L,
        "device" to "Test_Device"
    )

    private fun createGlucoseValue(timestamp: Instant, value: Double = 93.0) = GV(
        id = 10 * timestamp.toEpochMilli(),
        timestamp = timestamp.toEpochMilli(), raw = 90.0, value = value,
        trendArrow = TrendArrow.FLAT, noise = 4.5,
        sourceSensor = SourceSensor.RANDOM
    )

    @Test
    fun testReceiveHeartRateMap() {
        val hr = createHeartRate(80)
        gp.receiveHeartRate(hr, false)
        verify(loopHub).storeHeartRate(
            Instant.ofEpochSecond(hr["hrStart"] as Long),
            Instant.ofEpochSecond(hr["hrEnd"] as Long),
            80,
            hr["device"] as String
        )
    }

    @Test
    fun testReceiveHeartRateUri() {
        val hr = createHeartRate(99)
        val uri = createUri(hr)
        gp.receiveHeartRate(uri)
        verify(loopHub).storeHeartRate(
            Instant.ofEpochSecond(hr["hrStart"] as Long),
            Instant.ofEpochSecond(hr["hrEnd"] as Long),
            99,
            hr["device"] as String
        )
    }

    @Test
    fun testReceiveHeartRate_UriTestIsTrue() {
        val params = createHeartRate(99).toMutableMap()
        params["test"] = true
        val uri = createUri(params)
        gp.receiveHeartRate(uri)
    }

    @Test
    fun testGetGlucoseValues_NoLast() {
        val from = getGlucoseValuesFrom
        val prev = createGlucoseValue(clock.instant().minusSeconds(310))
        whenever(loopHub.getGlucoseValues(from, true)).thenReturn(listOf(prev))
        assertArrayEquals(arrayOf(prev), gp.getGlucoseValues().toTypedArray())
        verify(loopHub).getGlucoseValues(from, true)
    }

    @Test
    fun testGetGlucoseValues_NoNewLast() {
        val from = getGlucoseValuesFrom
        val lastTimestamp = clock.instant()
        val prev = createGlucoseValue(clock.instant())
        gp.newValue = mock<Condition>()
        whenever(loopHub.getGlucoseValues(from, true)).thenReturn(listOf(prev))
        gp.onNewBloodGlucose(listOf(createGlucoseValue(lastTimestamp)))
        assertArrayEquals(arrayOf(prev), gp.getGlucoseValues().toTypedArray())

        verify(gp.newValue).signalAll()
        verify(loopHub).getGlucoseValues(from, true)
    }

    @Test
    fun setupHttpServer_enabled() = runBlocking {
        whenever(preferences.get(GarminStringKey.RequestKey)).thenReturn("")
        whenever(preferences.get(GarminBooleanKey.LocalHttpServer)).thenReturn(true)
        whenever(preferences.get(GarminIntKey.LocalHttpPort)).thenReturn(28892)
        gp.setupHttpServer(Duration.ofSeconds(10))
        val reqUri = URI("http://127.0.0.1:28892/get")
        val resp = reqUri.toURL().openConnection() as HttpURLConnection
        assertEquals(200, resp.responseCode)

        // Change port
        whenever(preferences.get(GarminIntKey.LocalHttpPort)).thenReturn(28893)
        gp.setupHttpServer(Duration.ofSeconds(10))
        val reqUri2 = URI("http://127.0.0.1:28893/get")
        val resp2 = reqUri2.toURL().openConnection() as HttpURLConnection
        assertEquals(200, resp2.responseCode)

        whenever(preferences.get(GarminBooleanKey.LocalHttpServer)).thenReturn(false)
        gp.setupHttpServer(Duration.ofSeconds(10))
        assertThrows(ConnectException::class.java) {
            (reqUri2.toURL().openConnection() as HttpURLConnection).responseCode
        }
        gp.onStop()

        verify(loopHub, times(2)).getGlucoseValues(anyOrNull(), eq(true))
        verify(loopHub, times(2)).insulinOnboard
        verify(loopHub, times(2)).temporaryBasal
        verify(loopHub, times(2)).isConnected
        verify(loopHub, times(2)).glucoseUnit
        verify(loopHub, times(2)).lowGlucoseMark
        verify(loopHub, times(2)).highGlucoseMark
    }

    @Test
    fun setupHttpServer_disabled() {
        gp.setupHttpServer(Duration.ofSeconds(10))
        val reqUri = URI("http://127.0.0.1:28890/get")
        assertThrows(ConnectException::class.java) {
            (reqUri.toURL().openConnection() as HttpURLConnection).responseCode
        }
    }

    @Test
    fun requestHandler_NoKey() {
        whenever(preferences.get(GarminStringKey.RequestKey)).thenReturn("")
        val uri = createUri(emptyMap())
        val handler = gp.requestHandler { u: URI -> assertEquals(uri, u); "OK" }
        assertEquals(
            HttpURLConnection.HTTP_OK to "OK",
            handler(mock<SocketAddress>(), uri, null)
        )
    }

    @Test
    fun requestHandler_KeyProvided() {
        whenever(preferences.get(GarminStringKey.RequestKey)).thenReturn("")
        val uri = createUri(mapOf("key" to "foo"))
        val handler = gp.requestHandler { u: URI -> assertEquals(uri, u); "OK" }
        assertEquals(
            HttpURLConnection.HTTP_OK to "OK",
            handler(mock<SocketAddress>(), uri, null)
        )
    }

    @Test
    fun requestHandler_KeyRequiredAndProvided() {
        whenever(preferences.get(GarminStringKey.RequestKey)).thenReturn("foo")
        val uri = createUri(mapOf("key" to "foo"))
        val handler = gp.requestHandler { u: URI -> assertEquals(uri, u); "OK" }
        assertEquals(
            HttpURLConnection.HTTP_OK to "OK",
            handler(mock<SocketAddress>(), uri, null)
        )

    }

    @Test
    fun requestHandler_KeyRequired() {
        gp.garminMessengerField = mock<GarminMessenger>()

        whenever(preferences.get(GarminStringKey.RequestKey)).thenReturn("foo")
        val uri = createUri(emptyMap())
        val handler = gp.requestHandler { u: URI -> assertEquals(uri, u); "OK" }
        assertEquals(
            HttpURLConnection.HTTP_UNAUTHORIZED to "{}",
            handler(mock<SocketAddress>(), uri, null)
        )

        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(gp.garminMessenger).sendMessage(captor.capture() ?: "")
        @Suppress("UNCHECKED_CAST")
        val r = captor.value as Map<String, Any>
        assertEquals("foo", r["key"])
        assertEquals("glucose", r["command"])
        assertEquals("D", r["profile"])
        assertEquals("", r["encodedGlucose"])
        assertEquals(0.0, r["remainingInsulin"])
        assertEquals("mmoll", r["glucoseUnit"])
        assertEquals(0.0, r["temporaryBasalRate"])
        assertEquals(false, r["connected"])
        assertEquals(clock.instant().epochSecond, r["timestamp"])
        verify(loopHub).getGlucoseValues(getGlucoseValuesFrom, true)
        verify(loopHub).insulinOnboard
        verify(loopHub).temporaryBasal
        verify(loopHub).isConnected
        verify(loopHub).glucoseUnit
    }

    @Test
    fun onConnectDevice() {
        gp.garminMessengerField = mock<GarminMessenger>()
        whenever(preferences.get(GarminStringKey.RequestKey)).thenReturn("foo")
        val device = GarminDevice(mock(), 1, "Edge")
        gp.onConnectDevice(device)

        val captor = ArgumentCaptor.forClass(Any::class.java)
        verify(gp.garminMessenger).sendMessage(eq(device), captor.capture() ?: "")
        @Suppress("UNCHECKED_CAST")
        val r = captor.value as Map<String, Any>
        assertEquals("foo", r["key"])
        assertEquals("glucose", r["command"])
        assertEquals("D", r["profile"])
        assertEquals("", r["encodedGlucose"])
        assertEquals(0.0, r["remainingInsulin"])
        assertEquals("mmoll", r["glucoseUnit"])
        assertEquals(0.0, r["temporaryBasalRate"])
        assertEquals(false, r["connected"])
        assertEquals(clock.instant().epochSecond, r["timestamp"])
        verify(loopHub).getGlucoseValues(getGlucoseValuesFrom, true)
        verify(loopHub).insulinOnboard
        verify(loopHub).temporaryBasal
        verify(loopHub).isConnected
        verify(loopHub).glucoseUnit
    }

    @Test
    fun testOnGetBloodGlucose() {
        whenever(loopHub.isConnected).thenReturn(true)
        whenever(loopHub.insulinOnboard).thenReturn(3.14)
        whenever(loopHub.insulinBasalOnboard).thenReturn(2.71)
        whenever(loopHub.temporaryBasal).thenReturn(0.8)
        whenever(loopHub.lowGlucoseMark).thenReturn(70.0)
        whenever(loopHub.highGlucoseMark).thenReturn(130.0)
        val from = getGlucoseValuesFrom
        whenever(loopHub.getGlucoseValues(from, true)).thenReturn(
            listOf(createGlucoseValue(Instant.ofEpochSecond(1_000)))
        )
        val hr = createHeartRate(99)
        val uri = createUri(hr)
        val result = gp.onGetBloodGlucose(uri)
        assertEquals(
            """{"encodedGlucose":"0A+6AQ==",""" +
                """"remainingInsulin":3.14,"remainingBasalInsulin":2.71,""" +
                """"lowGlucoseMark":70,"highGlucoseMark":130,""" +
                """"glucoseUnit":"mmoll","temporaryBasalRate":0.8,""" +
                """"profile":"D","connected":true}""",
            result.toString()
        )
        verify(loopHub).getGlucoseValues(from, true)
        verify(loopHub).insulinOnboard
        verify(loopHub).temporaryBasal
        verify(loopHub).isConnected
        verify(loopHub).glucoseUnit
        verify(loopHub).lowGlucoseMark
        verify(loopHub).highGlucoseMark
        verify(loopHub).storeHeartRate(
            Instant.ofEpochSecond(hr["hrStart"] as Long),
            Instant.ofEpochSecond(hr["hrEnd"] as Long),
            99,
            hr["device"] as String
        )
    }

    @Test
    fun testOnGetBloodGlucose_Wait() {
        whenever(loopHub.isConnected).thenReturn(true)
        whenever(loopHub.insulinOnboard).thenReturn(3.14)
        whenever(loopHub.temporaryBasal).thenReturn(0.8)
        whenever(loopHub.glucoseUnit).thenReturn(GlucoseUnit.MMOL)
        val from = getGlucoseValuesFrom
        whenever(loopHub.getGlucoseValues(from, true)).thenReturn(
            listOf(createGlucoseValue(clock.instant().minusSeconds(330)))
        )
        val params = createHeartRate(99).toMutableMap()
        params["wait"] = 10
        val uri = createUri(params)
        gp.newValue = mock<Condition>()
        val result = gp.onGetBloodGlucose(uri)
        assertEquals(
            """{"encodedGlucose":"/wS6AQ==",""" +
                """"remainingInsulin":3.14,"remainingBasalInsulin":0.0,""" +
                """"glucoseUnit":"mmoll","temporaryBasalRate":0.8,""" +
                """"profile":"D","connected":true}""",
            result.toString()
        )
        verify(gp.newValue).awaitNanos(anyLong())
        verify(loopHub, times(2)).getGlucoseValues(from, true)
        verify(loopHub).insulinOnboard
        verify(loopHub).temporaryBasal
        verify(loopHub).isConnected
        verify(loopHub).glucoseUnit
        verify(loopHub).lowGlucoseMark
        verify(loopHub).highGlucoseMark
        verify(loopHub).storeHeartRate(
            Instant.ofEpochSecond(params["hrStart"] as Long),
            Instant.ofEpochSecond(params["hrEnd"] as Long),
            99,
            params["device"] as String
        )
    }

    @Test
    fun testOnPostCarbs() {
        val uri = createUri(mapOf("carbs" to "12"))
        assertEquals("", gp.onPostCarbs(uri))
        verify(loopHub).postCarbs(12)
    }

    @Test
    fun testOnPostBolus() {
        whenever(preferences.get(GarminBooleanKey.RemoteBolus)).thenReturn(true)
        assertEquals("", gp.onPostBolus(createUri(mapOf("bolus" to "1.5"))))
        verify(loopHub).postBolus(1.5)
    }

    @Test
    fun testOnPostBolus_SettingOff() {
        whenever(preferences.get(GarminBooleanKey.RemoteBolus)).thenReturn(false)
        assertEquals("", pluginWithText().onPostBolus(createUri(mapOf("bolus" to "1.5"))))
        verify(loopHub, never()).postBolus(any())
        assertEquals(
            "The Garmin watch asked for a bolus of 1.50 U. No bolus was given, because bolus from Garmin is not allowed. Turn it on in the Garmin settings.",
            postedNotice(NotificationId.GARMIN_BOLUS_DISABLED)
        )
    }

    @Test
    fun testOnPostBolus_InvalidValue() {
        whenever(preferences.get(GarminBooleanKey.RemoteBolus)).thenReturn(true)
        for (uri in listOf(createUri(emptyMap()), createUri(mapOf("bolus" to "abc")), createUri(mapOf("bolus" to "0")), createUri(mapOf("bolus" to "-2")))) {
            assertEquals("", gp.onPostBolus(uri))
        }
        verify(loopHub, never()).postBolus(any())
    }

    @Test
    fun testOnPostBolus_OnClient() {
        whenever(preferences.get(GarminBooleanKey.RemoteBolus)).thenReturn(true)
        assertEquals("", clientPlugin().onPostBolus(createUri(mapOf("bolus" to "1.5"))))
        verify(loopHub, never()).postBolus(any())
        assertEquals("The Garmin watch asked for a bolus of 1.50 U. This phone is a client, so no bolus was given. Use AAPS to give it.", postedNotice())
    }

    @Test
    fun testOnConnectPump_Disconnect() {
        val uri = createUri(mapOf("disconnectMinutes" to "20"))
        whenever(loopHub.isConnected).thenReturn(false)
        assertEquals("{\"connected\":false}", gp.onConnectPump(uri))
        verify(loopHub).disconnectPump(20)
        verify(loopHub).isConnected
    }

    @Test
    fun testOnConnectPump_Connect() {
        val uri = createUri(mapOf("disconnectMinutes" to "0"))
        whenever(loopHub.isConnected).thenReturn(true)
        assertEquals("{\"connected\":true}", gp.onConnectPump(uri))
        verify(loopHub).connectPump()
        verify(loopHub).isConnected
    }

    // ---- Steps from a total of the day ("steps=...") ----

    private fun stepRecord(device: String) = SC(
        duration = 300_000L, timestamp = 5_000L, steps5min = 14481, steps10min = 0, steps15min = 0, steps30min = 0,
        steps60min = 0, steps180min = 0, device = device, dateCreated = 5_000L
    )

    private fun recordsToday(vararg records: SC) = runBlocking {
        whenever(persistenceLayer.getStepsCountFromTimeToTime(anyLong(), anyLong())).thenReturn(records.toList())
    }

    private fun verifyNoStepsStored() = verify(loopHub, never()).storeStepsCount(
        any(), any(), any<Int>(), any<Int>(), any<Int>(), any<Int>(), any<Int>(), any<Int>(), anyOrNull()
    )

    private fun verifyStepsStored(steps: Int, device: String) = verify(loopHub).storeStepsCount(
        any(), any(), eq(steps), eq(0), eq(0), eq(0), eq(0), eq(0), eq(device)
    )

    private val firstRequestAt = Instant.ofEpochMilli(1_000_000)

    /**
     * Sends two requests with a total, 5 minutes apart. The first one only sets the baseline (it is the first value after
     * the start), so the second one is a request in a continuous row. [first] is also what the stored last total is.
     */
    private fun sendTotals(first: Int, second: Int, device: String? = null) {
        val extra: Map<String, Any> = if (device == null) emptyMap() else mapOf("device" to device)
        whenever(preferences.get(GarminIntNonKey.LastTotalSteps)).thenReturn(first)
        gp.clock = Clock.fixed(firstRequestAt, ZoneId.of("UTC"))
        gp.receiveSteps(createUri(mapOf<String, Any>("steps" to first) + extra))
        gp.clock = Clock.fixed(firstRequestAt.plus(5, ChronoUnit.MINUTES), ZoneId.of("UTC"))
        gp.receiveSteps(createUri(mapOf<String, Any>("steps" to second) + extra))
    }

    @Test
    fun `the same total without a device is not stored again when the record exists`() {
        // The record was stored as "Garmin", because the watch sent no device. The look-up must find it.
        recordsToday(stepRecord("Garmin"))

        sendTotals(14481, 14481)

        verifyNoStepsStored()
    }

    @Test
    fun `a small first total of the day is stored as the steps of the window`() {
        recordsToday()

        sendTotals(165, 165)

        verifyStepsStored(165, "Garmin")
    }

    @Test
    fun `a first total that is a whole day is not stored as the steps of the window`() {
        recordsToday()

        sendTotals(14481, 14481)

        verifyNoStepsStored()
    }

    @Test
    fun `a new day in a continuous row keeps the baseline but stores no steps when the total is a whole day`() {
        // The watch starts at 0 at midnight. A total of 8000 after 17506 is not the steps of one window.
        sendTotals(17506, 8000)

        verify(preferences).put(GarminIntNonKey.LastTotalSteps, 8000)
        verifyNoStepsStored()
    }

    @Test
    fun `a new day in a continuous row with a small total is stored`() {
        sendTotals(17506, 165, device = "Test_Device")

        verifyStepsStored(165, "Test_Device")
    }

    @Test
    fun `a rising total is stored as the difference`() {
        sendTotals(14481, 14526)

        verifyStepsStored(45, "Garmin")
    }

    @Test
    fun `the first value after the start of the app is only the baseline`() {
        whenever(preferences.get(GarminIntNonKey.LastTotalSteps)).thenReturn(14000)

        gp.receiveSteps(createUri(mapOf("steps" to 14526)))

        verify(preferences).put(GarminIntNonKey.LastTotalSteps, 14526)
        verifyNoStepsStored()
    }

    @Test
    fun `the first value after a gap is only the baseline and the next difference is stored again`() {
        fun request(minutes: Long, lastTotal: Int, total: Int) {
            whenever(preferences.get(GarminIntNonKey.LastTotalSteps)).thenReturn(lastTotal)
            gp.clock = Clock.fixed(firstRequestAt.plus(minutes, ChronoUnit.MINUTES), ZoneId.of("UTC"))
            gp.receiveSteps(createUri(mapOf("steps" to total)))
        }
        request(0, 14481, 14481) // first value after the start: baseline
        request(5, 14481, 14526) // continuous: 45 steps
        request(25, 14526, 15500) // 20 minutes later: a gap, so only the baseline (974 steps are lost on purpose)
        request(30, 15500, 15530) // continuous again: 30 steps

        verifyStepsStored(45, "Garmin")
        verifyStepsStored(30, "Garmin")
        verify(loopHub, never()).storeStepsCount(any(), any(), eq(974), any<Int>(), any<Int>(), any<Int>(), any<Int>(), any<Int>(), anyOrNull())
    }

    // A client only shows values. Carbs or a pump disconnect stored there would never reach the master, and the watch
    // cannot show why, so the phone says it.
    private fun clientPlugin(): GarminPlugin {
        whenever(config.AAPSCLIENT).thenReturn(true)
        return pluginWithText()
    }

    /** A plugin with the real English texts, for tests that check the notice on the phone. */
    private fun pluginWithText(): GarminPlugin {
        val text = generatedTextResolver("sync" to SyncStringsValues::textOf)
        return GarminPlugin(aapsLogger, text, preferences, context, loopHub, persistenceLayer, config, notificationManager)
    }

    private fun postedNotice(id: NotificationId = NotificationId.GARMIN_NOT_ON_CLIENT): String {
        val captor = argumentCaptor<String>()
        verify(notificationManager).post(eq(id), captor.capture(), any(), any<Int>(), anyOrNull(), any(), anyOrNull())
        return captor.firstValue
    }

    @Test
    fun testOnPostCarbs_OnClient() {
        val uri = createUri(mapOf("carbs" to "12"))
        assertEquals("", clientPlugin().onPostCarbs(uri))
        assertEquals("The Garmin watch sent 12 g of carbs. This phone is a client, so they were not entered. Enter them in AAPS.", postedNotice())
    }

    @Test
    fun testOnConnectPump_DisconnectOnClient() {
        val uri = createUri(mapOf("disconnectMinutes" to "20"))
        whenever(loopHub.isConnected).thenReturn(true)
        assertEquals("{\"connected\":true}", clientPlugin().onConnectPump(uri))
        verify(loopHub).isConnected
        assertEquals(
            "The Garmin watch asked to connect or disconnect the pump. This phone is a client, so nothing was changed. Do it in AAPS.",
            postedNotice()
        )
    }

    @Test
    fun onSgv_NoGlucose() {
        whenever(loopHub.glucoseUnit).thenReturn(GlucoseUnit.MMOL)
        whenever(loopHub.getGlucoseValues(any(), eq(false))).thenReturn(emptyList())
        assertEquals("[]", gp.onSgv(createUri(mapOf())))
        verify(loopHub).getGlucoseValues(clock.instant().minusSeconds(25L * 300L), false)
    }

    @Test
    fun onSgv_NoDelta() {
        whenever(loopHub.glucoseUnit).thenReturn(GlucoseUnit.MMOL)
        whenever(loopHub.insulinOnboard).thenReturn(2.7)
        whenever(loopHub.insulinBasalOnboard).thenReturn(2.5)
        whenever(loopHub.temporaryBasal).thenReturn(0.8)
        whenever(loopHub.carbsOnboard).thenReturn(10.7)
        whenever(loopHub.getGlucoseValues(any(), eq(false))).thenReturn(
            listOf(
                createGlucoseValue(
                    clock.instant().minusSeconds(100L), 99.3
                )
            )
        )
        assertEquals(
            """[{"_id":"-900000","device":"RANDOM","deviceString":"1969-12-31T23:58:30Z","sysTime":"1969-12-31T23:58:30Z","unfiltered":90.0,"date":-90000,"sgv":99,"direction":"Flat","noise":4.5,"units_hint":"mmol","iob":5.2,"tbr":80,"cob":10.7}]""",
            gp.onSgv(createUri(mapOf()))
        )
        verify(loopHub).getGlucoseValues(clock.instant().minusSeconds(25L * 300L), false)
        verify(loopHub).glucoseUnit
    }

    @Test
    fun onSgv() {
        whenever(loopHub.glucoseUnit).thenReturn(GlucoseUnit.MMOL)
        whenever(loopHub.insulinOnboard).thenReturn(2.7)
        whenever(loopHub.insulinBasalOnboard).thenReturn(2.5)
        whenever(loopHub.temporaryBasal).thenReturn(0.8)
        whenever(loopHub.carbsOnboard).thenReturn(10.7)
        whenever(loopHub.getGlucoseValues(any(), eq(false))).thenAnswer { i ->
            val from = i.getArgument<Instant>(0)
            fromClosedRange(from.toEpochMilli(), clock.instant().toEpochMilli(), 300_000L)
                .map(Instant::ofEpochMilli)
                .mapIndexed { idx, ts -> createGlucoseValue(ts, 100.0 + (10 * idx)) }.reversed()
        }
        assertEquals(
            """[{"_id":"100000","device":"RANDOM","deviceString":"1970-01-01T00:00:10Z","sysTime":"1970-01-01T00:00:10Z","unfiltered":90.0,"date":10000,"sgv":120,"delta":10,"direction":"Flat","noise":4.5,"units_hint":"mmol","iob":5.2,"tbr":80,"cob":10.7}]""",
            gp.onSgv(createUri(mapOf("count" to "1")))
        )
        verify(loopHub).getGlucoseValues(
            clock.instant().minusSeconds(600L), false
        )


        assertEquals(
            """[{"_id":"100000","device":"RANDOM","deviceString":"1970-01-01T00:00:10Z","sysTime":"1970-01-01T00:00:10Z","unfiltered":90.0,"date":10000,"sgv":130,"delta":10,"direction":"Flat","noise":4.5,"units_hint":"mmol","iob":5.2,"tbr":80,"cob":10.7},""" +
                """{"_id":"-2900000","device":"RANDOM","deviceString":"1969-12-31T23:55:10Z","sysTime":"1969-12-31T23:55:10Z","unfiltered":90.0,"date":-290000,"sgv":120,"delta":10,"direction":"Flat","noise":4.5}]""",
            gp.onSgv(createUri(mapOf("count" to "2")))
        )
        verify(loopHub).getGlucoseValues(
            clock.instant().minusSeconds(900L), false
        )

        assertEquals(
            """[{"date":10000,"sgv":130,"delta":10,"direction":"Flat","noise":4.5,"units_hint":"mmol","iob":5.2,"tbr":80,"cob":10.7},""" +
                """{"date":-290000,"sgv":120,"delta":10,"direction":"Flat","noise":4.5}]""",
            gp.onSgv(createUri(mapOf("count" to "2", "brief_mode" to "true")))
        )
        verify(loopHub, times(2)).getGlucoseValues(
            clock.instant().minusSeconds(900L), false
        )

        verify(loopHub, atLeastOnce()).glucoseUnit
    }

}
