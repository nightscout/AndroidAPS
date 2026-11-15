package app.aaps.plugins.sync.tidepool.messages

import com.google.common.truth.Truth.assertThat
import com.google.gson.Gson
import org.junit.jupiter.api.Test

/**
 * Tests reading the dataset reply of Tidepool. The upload id can come in two shapes: inside `data`
 * (answer of "open dataset") or in the root of the reply (answer of "list open datasets"), and the
 * upload calls need it either way.
 *
 * Uses a plain [Gson] like the Retrofit converter in
 * `TidepoolUploader.getRetrofitInstance`, not the @Expose-only instance used for sending.
 */
class DatasetReplyMessageTest {

    private val gson = Gson()

    @Test
    fun `upload id inside data is used`() {
        val json = """{"data":{"uploadId":"upload-1","id":"data-1","type":"upload"}}"""

        val reply = gson.fromJson(json, DatasetReplyMessage::class.java)

        assertThat(reply.getUploadId()).isEqualTo("upload-1")
    }

    @Test
    fun `upload id in the root is used when there is no data`() {
        val json = """{"id":"dataset-1","uploadId":"upload-2"}"""

        val reply = gson.fromJson(json, DatasetReplyMessage::class.java)

        assertThat(reply.getUploadId()).isEqualTo("upload-2")
    }

    @Test
    fun `data wins over the root`() {
        val json = """{"uploadId":"upload-root","data":{"uploadId":"upload-data"}}"""

        val reply = gson.fromJson(json, DatasetReplyMessage::class.java)

        assertThat(reply.getUploadId()).isEqualTo("upload-data")
    }

    @Test
    fun `reply without any upload id gives null`() {
        val reply = gson.fromJson("""{"id":"dataset-1"}""", DatasetReplyMessage::class.java)

        assertThat(reply.getUploadId()).isNull()
    }
}
