package info.nightscout.plugins.sync.tidepool.comm

import info.nightscout.plugins.sync.tidepool.messages.AuthReplyMessage
import info.nightscout.plugins.sync.tidepool.messages.DatasetReplyMessage
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class SessionTest {

    @Test
    fun populateBody() {
        val session = Session("", "", null)
        Assertions.assertNull(session.authReply)

        // test authReply
        val authReplyMessage = AuthReplyMessage()
        session.populateBody(authReplyMessage)
        Assertions.assertEquals(authReplyMessage, session.authReply)

        // test datasetReply
        val datasetReplyMessage = DatasetReplyMessage()
        Assertions.assertNull(session.datasetReply)
        session.populateBody(datasetReplyMessage)
        Assertions.assertEquals(datasetReplyMessage, session.datasetReply)

        // test datasetReply as array
        val list: List<DatasetReplyMessage> = listOf(datasetReplyMessage)
        session.datasetReply = null
        session.populateBody(list)
        Assertions.assertEquals(datasetReplyMessage, session.datasetReply)
    }
}