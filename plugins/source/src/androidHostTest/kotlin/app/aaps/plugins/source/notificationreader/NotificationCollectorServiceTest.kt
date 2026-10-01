package app.aaps.plugins.source.notificationreader

import android.app.Notification
import android.os.Bundle
import android.service.notification.StatusBarNotification
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.source.NotificationReaderPlugin
import app.aaps.shared.tests.AAPSLoggerTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric test for [NotificationCollectorService]. The service is instantiated directly (its
 * onCreate uses AndroidInjection, which is unavailable in tests), the injected fields are set, and
 * the private parser/deduplicator are replaced with mocks so onNotificationPosted can be driven.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NotificationCollectorServiceTest {

    private val plugin: NotificationReaderPlugin = mock()
    private val packageConfig: PackageConfig = mock()
    private val persistenceLayer: PersistenceLayer = mock()
    private val profileFunction: ProfileFunction = mock()
    private val preferences: Preferences = mock()
    private val parser: NotificationParser = mock()
    private val deduplicator: GlucoseDeduplicator = mock()

    private lateinit var service: NotificationCollectorService

    @Before
    fun setup() {
        service = NotificationCollectorService()
        service.aapsLogger = AAPSLoggerTest()
        service.notificationReaderPlugin = plugin
        service.persistenceLayer = persistenceLayer
        service.profileFunction = profileFunction
        service.preferences = preferences
        setPrivate("parser", parser)
        setPrivate("deduplicator", deduplicator)
        whenever(plugin.packageConfig).thenReturn(packageConfig)
        whenever(profileFunction.getUnits()).thenReturn(GlucoseUnit.MGDL)
    }

    private fun setPrivate(name: String, value: Any?) {
        NotificationCollectorService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(service, value)
    }

    private fun sbn(pkg: String): StatusBarNotification {
        val notification = Notification().apply {
            extras = Bundle().apply {
                putCharSequence(Notification.EXTRA_TITLE, "Glucose 100 mg/dL")
                putCharSequence(Notification.EXTRA_TEXT, "Flat")
            }
        }
        return mock<StatusBarNotification>().also {
            whenever(it.packageName).thenReturn(pkg)
            whenever(it.notification).thenReturn(notification)
        }
    }

    private fun verifyNoInsert() = runBlocking {
        verify(persistenceLayer, never()).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
    }

    @Test
    fun `unsupported package is ignored`() {
        whenever(packageConfig.isSupportedPackage(any())).thenReturn(false)

        service.onNotificationPosted(sbn("com.foo"))

        verifyNoInsert()
    }

    @Test
    fun `disabled plugin is ignored`() {
        whenever(packageConfig.isSupportedPackage(any())).thenReturn(true)
        whenever(plugin.isEnabled()).thenReturn(false)

        service.onNotificationPosted(sbn("com.foo"))

        verifyNoInsert()
    }

    @Test
    fun `valid glucose notification is inserted`() {
        whenever(packageConfig.isSupportedPackage(any())).thenReturn(true)
        whenever(plugin.isEnabled()).thenReturn(true)
        whenever(parser.extractGlucose(any(), any(), any())).thenReturn(NotificationParser.GlucoseResult(100, SourceSensor.DEXCOM_NATIVE_UNKNOWN))
        whenever(deduplicator.process(any(), any())).thenReturn(true)
        runBlocking {
            whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
                .thenReturn(PersistenceLayer.TransactionResult())
        }

        service.onNotificationPosted(sbn("com.foo"))

        runBlocking {
            verify(persistenceLayer, timeout(2000)).insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())
        }
    }

    @Test
    fun `duplicate notification is not inserted`() {
        whenever(packageConfig.isSupportedPackage(any())).thenReturn(true)
        whenever(plugin.isEnabled()).thenReturn(true)
        whenever(parser.extractGlucose(any(), any(), any())).thenReturn(NotificationParser.GlucoseResult(100, SourceSensor.DEXCOM_NATIVE_UNKNOWN))
        whenever(deduplicator.process(any(), any())).thenReturn(false)

        service.onNotificationPosted(sbn("com.foo"))

        verifyNoInsert()
    }

    @Test
    fun `onNotificationRemoved is a no-op`() {
        service.onNotificationRemoved(sbn("com.foo"))
    }
}
