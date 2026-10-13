package app.aaps.plugins.sync.tidepool.comm

import app.aaps.core.data.model.BS
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.plugins.sync.tidepool.elements.BolusElement
import app.aaps.plugins.sync.tidepool.utils.GsonInstance
import com.google.common.truth.Truth.assertThat
import com.google.gson.reflect.TypeToken
import io.reactivex.rxjava3.core.Single
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
    @Mock lateinit var rxBus: RxBus
    @Mock lateinit var aapsLogger: AAPSLogger

    @Suppress("unused")
    @Mock lateinit var profileFunction: ProfileFunction

    @Suppress("unused")
    @Mock lateinit var profileUtil: ProfileUtil

    @Suppress("unused")
    @Mock lateinit var activePlugin: ActivePlugin
    @Mock lateinit var persistenceLayer: PersistenceLayer
    @Mock lateinit var dateUtil: DateUtil

    @InjectMocks lateinit var sut: UploadChunk

    @Test
    fun `SMBs should be marked as 'automated' when uploading to Tidepool`() {
        // setup mocked test data
        val boluses = listOf(
            BS(timestamp = 100, amount = 7.5, type = BS.Type.NORMAL),
            BS(timestamp = 200, amount = 0.5, type = BS.Type.SMB)
        )
        whenever(persistenceLayer.getBolusesFromTimeToTime(any(), any(), any())).thenReturn(boluses)
        whenever(persistenceLayer.getTherapyEventDataFromToTime(any(), any())).thenReturn(Single.just(listOf()))

        // when
        val resultJson = sut.get(1, 500)

        // then
        val resultBolusElements = convertResultJsonToBolusElements(resultJson)
        assertThat(resultBolusElements[0].subType).isEqualTo("normal")
        assertThat(resultBolusElements[0].normal).isEqualTo(7.5)
        assertThat(resultBolusElements[1].subType).isEqualTo("automated")
        assertThat(resultBolusElements[1].normal).isEqualTo(0.5)
    }

    private fun convertResultJsonToBolusElements(json: String): List<BolusElement> {
        val itemType = object : TypeToken<List<BolusElement>>() {}.type
        return GsonInstance.defaultGsonInstance().fromJson(json, itemType)
    }
}
