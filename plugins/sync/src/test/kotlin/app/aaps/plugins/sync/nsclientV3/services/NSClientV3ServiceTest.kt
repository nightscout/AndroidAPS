package app.aaps.plugins.sync.nsclientV3.services

import android.content.Intent
import android.os.IBinder
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
import app.aaps.plugins.sync.nsShared.compose.NSClientRepositoryImpl
import app.aaps.plugins.sync.nsclient.data.NSDeviceStatusHandler
import app.aaps.plugins.sync.nsclientV3.NSClientV3Plugin
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.whenever

class NSClientV3ServiceTest : TestBaseWithProfile() {

    @Mock lateinit var nsIncomingDataProcessor: NsIncomingDataProcessor
    @Mock lateinit var storeDataForDb: StoreDataForDb

    // notificationManager from TestBaseWithProfile
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin

    private lateinit var nsClientMvvmRepository: NSClientRepositoryImpl
    private lateinit var sut: NSClientV3Service

    @BeforeEach
    fun init() {
        nsClientMvvmRepository = NSClientRepositoryImpl(rxBus, aapsLogger)
        sut = NSClientV3Service().also {
            it.aapsLogger = aapsLogger
            it.preferences = preferences
            it.fabricPrivacy = fabricPrivacy
            it.nsClientV3Plugin = nsClientV3Plugin
            it.config = config
            it.nsIncomingDataProcessor = nsIncomingDataProcessor
            it.storeDataForDb = storeDataForDb
            it.notificationManager = notificationManager
            it.nsDeviceStatusHandler = nsDeviceStatusHandler
            it.nsClientRepository = nsClientMvvmRepository
        }
    }

    @Test
    fun `initializeWebSockets does not create sockets when URL is empty`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("")

        sut.initializeWebSockets("Test")

        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
        assertThat(sut.wsConnected).isFalse()
    }

    @Test
    fun `initializeWebSockets creates storage socket when URL is valid and allowed`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("Test")

        assertThat(sut.storageSocket).isNotNull()
        assertThat(sut.alarmSocket).isNull()
        sut.shutdownWebsockets()
    }

    @Test
    fun `initializeWebSockets creates both sockets when notifications enabled`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("Test")

        assertThat(sut.storageSocket).isNotNull()
        assertThat(sut.alarmSocket).isNotNull()
        sut.shutdownWebsockets()
    }

    @Test
    fun `initializeWebSockets creates alarm socket when alarms enabled`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("Test")

        assertThat(sut.storageSocket).isNotNull()
        assertThat(sut.alarmSocket).isNotNull()
        sut.shutdownWebsockets()
    }

    @Test
    fun `initializeWebSockets does nothing when WS is disabled`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(false)

        sut.initializeWebSockets("Test")

        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
    }

    @Test
    fun `initializeWebSockets skips when storage socket already initialized`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("First")
        val firstSocket = sut.storageSocket
        assertThat(firstSocket).isNotNull()

        sut.initializeWebSockets("Second")

        assertThat(sut.storageSocket).isSameInstanceAs(firstSocket)
        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "● WS" && it.logText?.contains("already initialized, skip Second") ?: false }).isTrue()
        sut.shutdownWebsockets()
    }

    @Test
    fun `initializeWebSockets sends paused event when paused`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("Test")

        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "● WS" && it.logText == "paused" }).isTrue()
        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
    }

    @Test
    fun `initializeWebSockets sends blocking reason when not allowed`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(false)
        whenever(nsClientV3Plugin.blockingReason).thenReturn("No network connection")

        sut.initializeWebSockets("Test")

        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "● WS" && it.logText == "No network connection" }).isTrue()
        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
    }

    @Test
    fun `initializeWebSockets normalizes URL by removing trailing slash`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("HTTP://SOMETHING/")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("Test")

        assertThat(sut.storageSocket).isNotNull()
        sut.shutdownWebsockets()
    }

    @Test
    fun `shutdownWebsockets clears sockets and sets wsConnected to false`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("Test")
        assertThat(sut.storageSocket).isNotNull()
        assertThat(sut.alarmSocket).isNotNull()

        sut.shutdownWebsockets()

        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
        assertThat(sut.wsConnected).isFalse()
    }

    @Test
    fun `shutdownWebsockets handles null sockets gracefully`() {
        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()

        sut.shutdownWebsockets()

        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
        assertThat(sut.wsConnected).isFalse()
    }

    @Test
    fun `shutdownWebsockets is idempotent when called twice after init`() {
        // stopService() now calls shutdownWebsockets() before unbind, and the service's
        // onDestroy will call it again — verify the second call is a safe no-op.
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("Test")
        assertThat(sut.storageSocket).isNotNull()
        assertThat(sut.alarmSocket).isNotNull()

        sut.shutdownWebsockets()
        sut.shutdownWebsockets()

        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
        assertThat(sut.wsConnected).isFalse()
    }

    @Test
    fun `initializeWebSockets after shutdown creates fresh socket with new URL`() {
        // Simulates the restartOnChange cycle: init → URL change → shutdown → init.
        // The "already initialized" guard must not block re-creation after a clean shutdown.
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://first")
        sut.initializeWebSockets("First")
        val firstSocket = sut.storageSocket
        assertThat(firstSocket).isNotNull()

        sut.shutdownWebsockets()
        assertThat(sut.storageSocket).isNull()

        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://second")
        sut.initializeWebSockets("Restart")

        assertThat(sut.storageSocket).isNotNull()
        assertThat(sut.storageSocket).isNotSameInstanceAs(firstSocket)
        sut.shutdownWebsockets()
    }

    @Test
    fun `initializeWebSockets called with serviceConnected reason skips when socket already up`() {
        // Mirrors the new safety-net call in NSClientV3Plugin.onServiceConnected:
        // when Android reuses an already-alive service on rebind, onCreate doesn't fire,
        // but onServiceConnected does. The guard must prevent a duplicate subscribe.
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAlarms)).thenReturn(false)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("onCreate")
        val originalSocket = sut.storageSocket
        assertThat(originalSocket).isNotNull()

        sut.initializeWebSockets("serviceConnected")

        assertThat(sut.storageSocket).isSameInstanceAs(originalSocket)
        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "● WS" && it.logText?.contains("already initialized, skip serviceConnected") ?: false }).isTrue()
        sut.shutdownWebsockets()
    }

    @Test
    fun `onBind returns LocalBinder`() {
        val intent = Intent()

        val binder = sut.onBind(intent)

        assertThat(binder).isNotNull()
        assertThat(binder).isInstanceOf(IBinder::class.java)
    }

    @Test
    fun `LocalBinder returns service instance`() {
        val intent = Intent()
        val binder = sut.onBind(intent) as NSClientV3Service.LocalBinder

        val serviceInstance = binder.serviceInstance

        assertThat(serviceInstance).isEqualTo(sut)
    }

    @Test
    fun `wsConnected is initially false`() {
        assertThat(sut.wsConnected).isFalse()
    }

    @Test
    fun `initializeWebSockets sends connect events`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(BooleanKey.NsClient3UseWs)).thenReturn(true)
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        sut.initializeWebSockets("TestReason")

        val logs = nsClientMvvmRepository.logList.value
        assertThat(logs.any { it.action == "► WS" && it.logText?.contains("do connect storage TestReason") ?: false }).isTrue()
        assertThat(logs.any { it.action == "► WS" && it.logText?.contains("do connect alarm TestReason") ?: false }).isTrue()
        sut.shutdownWebsockets()
    }
}