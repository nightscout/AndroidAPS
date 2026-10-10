package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.data.model.BS
import app.aaps.core.data.model.ICfg
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.pump.PumpWithConcentration
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.compose.TidepoolRepository
import app.aaps.plugins.sync.tidepool.elements.BolusElement
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@ExtendWith(MockitoExtension::class)
class UploadChunkTest {

    @Mock lateinit var preferences: Preferences
    @Mock lateinit var tidepoolRepository: TidepoolRepository
    @Mock lateinit var aapsLogger: AAPSLogger

    @Suppress("unused")
    @Mock lateinit var profileFunction: ProfileFunction

    @Suppress("unused")
    @Mock lateinit var profileUtil: ProfileUtil

    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil
    @Mock lateinit var config: Config
    @Mock lateinit var pump: PumpWithConcentration

    @InjectMocks lateinit var sut: UploadChunk

    @BeforeEach
    fun setup() {
        // Every record's deviceId is built from the pump serial
        whenever(pump.serialNumber()).thenReturn("SN-1")
        whenever(activePlugin.activePump).thenReturn(pump)
    }

    val iCfg = ICfg(insulinLabel = "Fake", insulinEndTime = 9 * 3600 * 1000, insulinPeakTime = 60 * 60 * 1000, concentration = 1.0)

    @Test
    fun `SMBs should be marked as 'automated' when uploading to Tidepool`() = runTest {
        // setup mocked test data
        val boluses = listOf(
            BS(timestamp = 100, amount = 7.5, type = BS.Type.NORMAL, iCfg = iCfg),
            BS(timestamp = 200, amount = 0.5, type = BS.Type.SMB, iCfg = iCfg)
        )
        whenever(persistenceLayer.getBolusesFromTimeToTime(any(), any(), any())).thenReturn(boluses)
        whenever(persistenceLayer.getCarbsFromTimeToTimeExpanded(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getRunningModesFromTimeToTime(any(), any(), any())).thenReturn(listOf())

        // when
        val resultJson = sut.get(1, 500)

        // then
        val resultBolusElements = convertResultJsonToBolusElements(resultJson)
        assertThat(resultBolusElements[0].subType).isEqualTo("normal")
        assertThat(resultBolusElements[0].normal).isEqualTo(7.5)
        assertThat(resultBolusElements[1].subType).isEqualTo("automated")
        assertThat(resultBolusElements[1].normal).isEqualTo(0.5)
    }

    @Test
    fun `every record says which app sent it`() = runTest {
        whenever(config.APPLICATION_ID).thenReturn("info.nightscout.androidaps")
        whenever(config.VERSION_NAME).thenReturn("4.0.0")
        whenever(persistenceLayer.getBolusesFromTimeToTime(any(), any(), any())).thenReturn(listOf(BS(timestamp = 100, amount = 1.0, type = BS.Type.NORMAL, iCfg = iCfg)))
        whenever(persistenceLayer.getCarbsFromTimeToTimeExpanded(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getRunningModesFromTimeToTime(any(), any(), any())).thenReturn(listOf())

        val record = JsonParser.parseString(sut.get(1, 500)).asJsonArray[0].asJsonObject
        val origin = record["origin"].asJsonObject

        // One device for all records, the same id the pump settings use
        assertThat(record["deviceId"].asString).isEqualTo("AAPS:SN-1")
        // Tidepool asks for id, name and type, and recognises the app by name (as it does for Loop and Trio)
        assertThat(origin["id"].asString).isNotEmpty()
        assertThat(origin["name"].asString).isEqualTo("info.nightscout.androidaps")
        assertThat(origin["version"].asString).isEqualTo("4.0.0")
        assertThat(origin["type"].asString).isEqualTo("application")
    }

    @Test
    fun `a window with many records is split into batches of at most 1000`() = runTest {
        // Tidepool asks for "chunks of 1,000 records"; a full sync window can hold far more
        val boluses = (1..2500).map { BS(timestamp = it.toLong(), amount = 0.1, type = BS.Type.SMB, iCfg = iCfg) }
        whenever(persistenceLayer.getBolusesFromTimeToTime(any(), any(), any())).thenReturn(boluses)
        whenever(persistenceLayer.getCarbsFromTimeToTimeExpanded(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getBgReadingsDataFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getTemporaryBasalsStartingFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getEffectiveProfileSwitchesFromTimeToTime(any(), any(), any())).thenReturn(listOf())
        whenever(persistenceLayer.getRunningModesFromTimeToTime(any(), any(), any())).thenReturn(listOf())

        val batches = sut.getBatches(1, 5000)

        assertThat(batches.map { JsonParser.parseString(it).asJsonArray.size() }).containsExactly(1000, 1000, 500).inOrder()
    }

    private fun convertResultJsonToBolusElements(json: String): List<BolusElement> {
        val itemType = object : TypeToken<List<BolusElement>>() {}.type
        return GsonInstance.defaultGsonInstance().fromJson(json, itemType)
    }
}
