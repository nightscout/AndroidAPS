package app.aaps.plugins.sync.tidepool.messages

import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.plugins.sync.tidepool.comm.TidepoolUploader
import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import okio.Buffer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests the JSON that opens a Tidepool dataset. Two values in it are load bearing:
 * `client.name` must be the application id, because [TidepoolUploader.startSession] looks up the open
 * dataset by that name to reuse it, and the deduplicator name must stay
 * `org.tidepool.deduplicator.dataset.delete.origin`, because that is what lets a re-upload replace
 * older records instead of adding them a second time.
 */
class OpenDatasetRequestMessageTest {

    private val config: Config = mock()
    private val dateUtil: DateUtil = mock()

    private fun bodyAsJson() = Buffer().let { buffer ->
        OpenDatasetRequestMessage(config, dateUtil).getBody().writeTo(buffer)
        JsonParser.parseString(buffer.readUtf8()).asJsonObject
    }

    @Test
    fun `open dataset request holds the values Tidepool needs`() {
        whenever(config.APPLICATION_ID).thenReturn("info.nightscout.androidaps")
        whenever(config.VERSION_NAME).thenReturn("3.3.0")
        whenever(dateUtil.toISOAsUTC(any())).thenReturn("2026-08-05T10:00:00.000Z")
        whenever(dateUtil.toISONoZone(any())).thenReturn("2026-08-05T12:00:00")
        whenever(dateUtil.getTimeZoneOffsetMs()).thenReturn(7_200_000L) // +2 hours

        val json = bodyAsJson()

        assertThat(json["client"].asJsonObject["name"].asString).isEqualTo("info.nightscout.androidaps")
        assertThat(json["client"].asJsonObject["version"].asString).isEqualTo(TidepoolUploader.VERSION)
        assertThat(json["deduplicator"].asJsonObject["name"].asString).isEqualTo("org.tidepool.deduplicator.dataset.delete.origin")
        assertThat(json["dataSetType"].asString).isEqualTo("continuous")
        assertThat(json["type"].asString).isEqualTo("upload")
        assertThat(json["deviceModel"].asString).isEqualTo(TidepoolUploader.PUMP_TYPE)
        assertThat(json["version"].asString).isEqualTo("3.3.0")
        assertThat(json["timezoneOffset"].asInt).isEqualTo(120) // minutes
        assertThat(json["time"].asString).isEqualTo("2026-08-05T10:00:00.000Z")
        assertThat(json["computerTime"].asString).isEqualTo("2026-08-05T12:00:00")
    }
}
