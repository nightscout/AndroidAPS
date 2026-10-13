package app.aaps.plugins.sync.tidepool.comm

import app.aaps.plugins.sync.tidepool.messages.AuthReplyMessage
import app.aaps.plugins.sync.tidepool.messages.DatasetReplyMessage
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class SessionTest {

    private val session = Session("", null)

    @Test
    fun emptySession() {
        assertThat(session.authReply).isNull()
        assertThat(session.datasetReply).isNull()
    }

    @Test
    fun authReply() {
        val authReplyMessage = AuthReplyMessage()

        session.populateBody(authReplyMessage)

        assertThat(session.authReply).isEqualTo(authReplyMessage)
    }

    @Test
    fun datasetReply() {
        val datasetReplyMessage = DatasetReplyMessage()

        session.populateBody(datasetReplyMessage)

        assertThat(session.datasetReply).isEqualTo(datasetReplyMessage)
    }

    @Test
    fun datasetReply_asList() {
        val datasetReplyMessage = DatasetReplyMessage()

        session.populateBody(listOf(datasetReplyMessage))

        assertThat(session.datasetReply).isEqualTo(datasetReplyMessage)
    }
}
