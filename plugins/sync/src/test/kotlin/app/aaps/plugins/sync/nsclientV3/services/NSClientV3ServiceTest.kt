package app.aaps.plugins.sync.nsclientV3.services

import android.content.Intent
import android.os.IBinder
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.rx.events.EventNSClientNewLog
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.StringKey
import app.aaps.plugins.sync.nsShared.NsIncomingDataProcessor
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
    @Mock lateinit var uiInteraction: UiInteraction
    @Mock lateinit var nsDeviceStatusHandler: NSDeviceStatusHandler
    @Mock lateinit var nsClientV3Plugin: NSClientV3Plugin

    private lateinit var sut: NSClientV3Service

    @BeforeEach
    fun init() {
        sut = NSClientV3Service().also {
            it.aapsLogger = aapsLogger
            it.rxBus = rxBus
            it.rh = rh
            it.preferences = preferences
            it.fabricPrivacy = fabricPrivacy
            it.nsClientV3Plugin = nsClientV3Plugin
            it.config = config
            it.nsIncomingDataProcessor = nsIncomingDataProcessor
            it.storeDataForDb = storeDataForDb
            it.uiInteraction = uiInteraction
            it.nsDeviceStatusHandler = nsDeviceStatusHandler
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
    fun `initializeWebSockets sends paused event when paused`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(true)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        val events = mutableListOf<EventNSClientNewLog>()
        val subscription = rxBus.toObservable(EventNSClientNewLog::class.java).subscribe { events.add(it) }

        sut.initializeWebSockets("Test")

        assertThat(events.any { it.action == "● WS" && it.logText == "paused" }).isTrue()
        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
        subscription.dispose()
    }

    @Test
    fun `initializeWebSockets sends blocking reason when not allowed`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("http://something")
        whenever(nsClientV3Plugin.isAllowed).thenReturn(false)
        whenever(nsClientV3Plugin.blockingReason).thenReturn("No network connection")

        val events = mutableListOf<EventNSClientNewLog>()
        val subscription = rxBus.toObservable(EventNSClientNewLog::class.java).subscribe { events.add(it) }

        sut.initializeWebSockets("Test")

        assertThat(events.any { it.action == "● WS" && it.logText == "No network connection" }).isTrue()
        assertThat(sut.storageSocket).isNull()
        assertThat(sut.alarmSocket).isNull()
        subscription.dispose()
    }

    @Test
    fun `initializeWebSockets normalizes URL by removing trailing slash`() {
        whenever(preferences.get(StringKey.NsClientUrl)).thenReturn("HTTP://SOMETHING/")
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
        whenever(preferences.get(BooleanKey.NsClientNotificationsFromAnnouncements)).thenReturn(true)
        whenever(preferences.get(NsclientBooleanKey.NsPaused)).thenReturn(false)
        whenever(nsClientV3Plugin.isAllowed).thenReturn(true)

        val events = mutableListOf<EventNSClientNewLog>()
        val subscription = rxBus.toObservable(EventNSClientNewLog::class.java).subscribe { events.add(it) }

        sut.initializeWebSockets("TestReason")

        assertThat(events.any { it.action == "► WS" && it.logText?.contains("do connect storage TestReason") ?: false }).isTrue()
        assertThat(events.any { it.action == "► WS" && it.logText?.contains("do connect alarm TestReason") ?: false }).isTrue()
        subscription.dispose()
        sut.shutdownWebsockets()
    }
}