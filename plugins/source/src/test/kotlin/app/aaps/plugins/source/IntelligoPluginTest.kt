package app.aaps.plugins.source

import android.content.ContentResolver
import android.database.Cursor
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.SourceSensor
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.plugins.source.keys.IntelligoLongKey
import app.aaps.shared.tests.TestBaseWithProfile
import io.reactivex.rxjava3.core.Single
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IntelligoPluginTest : TestBaseWithProfile() {

    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var contentResolver: ContentResolver
    @Mock lateinit var cursor: Cursor

    private lateinit var intelligoPlugin: IntelligoPlugin

    @BeforeEach
    fun setup() {
        whenever(context.contentResolver).thenReturn(contentResolver)
        whenever(contentResolver.query(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(cursor)
        whenever(persistenceLayer.insertCgmSourceData(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull())).thenReturn(Single.just(PersistenceLayer.TransactionResult()))

        intelligoPlugin = IntelligoPlugin(rh, aapsLogger, preferences, context, persistenceLayer, dateUtil, fabricPrivacy)

        // Default cursor to be empty
        whenever(cursor.isAfterLast).thenReturn(true)
        whenever(cursor.moveToFirst()).thenReturn(false)
    }

    @Test
    fun `When plugin disabled then do nothing`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, false)

        // WHEN
        intelligoPlugin.handleNewData()

        // THEN
        verify(contentResolver, never()).query(any(), any(), any(), any(), any())
    }

    @Test
    fun `When new glucose and calibration data then insert it`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val now = 1678886400000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntelligoLongKey.LastProcessedTimestamp)).thenReturn(0L)

        // cursor mock for 1 glucose and 1 calibration
        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.isAfterLast).thenReturn(false, false, true)
        whenever(cursor.moveToNext()).thenReturn(true, true)
        whenever(cursor.getLong(0)).thenReturn(now - 1000, now - 500)
        whenever(cursor.getDouble(1)).thenReturn(8.0, 9.0) // mmol/l
        whenever(cursor.getDouble(2)).thenReturn(1.0, 0.0) // glucose, then calibration

        // WHEN
        intelligoPlugin.refreshLoop.run()

        // THEN
        val expectedGv = GV(
            timestamp = now - 1000,
            value = 8.0 * Constants.MMOLL_TO_MGDL,
            raw = 0.0,
            noise = null,
            trendArrow = TrendArrow.NONE,
            sourceSensor = SourceSensor.INTELLIGO_NATIVE
        )
        val expectedCalibration = PersistenceLayer.Calibration(
            timestamp = now - 500,
            value = 9.0,
            glucoseUnit = GlucoseUnit.MMOL
        )
        verify(persistenceLayer).insertCgmSourceData(Sources.Intelligo, listOf(expectedGv), listOf(expectedCalibration), null)
        verify(preferences).put(IntelligoLongKey.LastProcessedTimestamp, now - 500)
    }

    @Test
    fun `When data is old then skip it`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val now = 1678886400000L
        val lastProcessed = now - 2000
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntelligoLongKey.LastProcessedTimestamp)).thenReturn(lastProcessed)

        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.isAfterLast).thenReturn(false, true)
        whenever(cursor.moveToNext()).thenReturn(true)

        whenever(cursor.getLong(0)).thenReturn(lastProcessed - 1000) // older than last processed

        // WHEN
        intelligoPlugin.handleNewData()

        // THEN
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `When data is in future then skip it`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val now = 1678886400000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntelligoLongKey.LastProcessedTimestamp)).thenReturn(0L)

        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.isAfterLast).thenReturn(false, true)
        whenever(cursor.moveToNext()).thenReturn(true)

        whenever(cursor.getLong(0)).thenReturn(now + 1000) // future timestamp

        // WHEN
        intelligoPlugin.handleNewData()

        // THEN
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `When glucose value is out of bounds (low) then skip it`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val now = 1678886400000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntelligoLongKey.LastProcessedTimestamp)).thenReturn(0L)

        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.isAfterLast).thenReturn(false, true)
        whenever(cursor.moveToNext()).thenReturn(true)

        whenever(cursor.getLong(0)).thenReturn(now - 1000)
        whenever(cursor.getDouble(1)).thenReturn(1.9) // out of bounds (< 2)

        // WHEN
        intelligoPlugin.handleNewData()

        // THEN
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `When glucose value is out of bounds (high) then skip it`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val now = 1678886400000L
        whenever(dateUtil.now()).thenReturn(now)
        whenever(preferences.get(IntelligoLongKey.LastProcessedTimestamp)).thenReturn(0L)

        whenever(cursor.moveToFirst()).thenReturn(true)
        whenever(cursor.isAfterLast).thenReturn(false, true)
        whenever(cursor.moveToNext()).thenReturn(true)

        whenever(cursor.getLong(0)).thenReturn(now - 1000)
        whenever(cursor.getDouble(1)).thenReturn(25.1) // out of bounds (> 25)

        // WHEN
        intelligoPlugin.handleNewData()

        // THEN
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `When content resolver throws SecurityException then log error`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val exception = SecurityException("Permission denied")
        whenever(contentResolver.query(any(), any(), any(), any(), any())).thenThrow(exception)

        // WHEN
        intelligoPlugin.handleNewData()

        // THEN
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
    }

    @Test
    fun `When content resolver query returns null then do nothing`() {
        // GIVEN
        intelligoPlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        whenever(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(null)

        // WHEN
        intelligoPlugin.handleNewData()

        // THEN
        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), anyOrNull())
        verify(fabricPrivacy, never()).logException(any())
    }

    @Test
    fun startStopTest() {
        Assertions.assertNull(intelligoPlugin.handler)
        intelligoPlugin.onStart()
        Assertions.assertNotNull(intelligoPlugin.handler)
        intelligoPlugin.onStop()
        Assertions.assertNull(intelligoPlugin.handler)
    }
}
