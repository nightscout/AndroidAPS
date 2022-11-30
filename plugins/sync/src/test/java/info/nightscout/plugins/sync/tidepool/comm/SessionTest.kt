package info.nightscout.plugins.sync.tidepool.comm

import info.nightscout.plugins.sync.tidepool.messages.AuthReplyMessage
import info.nightscout.plugins.sync.tidepool.messages.DatasetReplyMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.jupiter.api.Test

class SessionTest {

    @Test
    fun populateBody() {
        val session = Session("", "", null)
        assertNull(session.authReply)

        // test authReply
        val authReplyMessage = AuthReplyMessage()
        session.populateBody(authReplyMessage)
        assertEquals(authReplyMessage, session.authReply)

        // test datasetReply
        val datasetReplyMessage = DatasetReplyMessage()
        assertNull(session.datasetReply)
        session.populateBody(datasetReplyMessage)
        assertEquals(datasetReplyMessage, session.datasetReply)

        // test datasetReply as array
        val list: List<DatasetReplyMessage> = listOf(datasetReplyMessage)
        session.datasetReply = null
        assertNull(session.datasetReply)
        session.populateBody(list)
        assertEquals(datasetReplyMessage, session.datasetReply)

    }
}