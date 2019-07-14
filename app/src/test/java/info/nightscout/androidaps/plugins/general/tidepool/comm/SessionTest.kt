package info.nightscout.androidaps.plugins.general.tidepool.comm

import info.nightscout.androidaps.plugins.general.tidepool.messages.AuthReplyMessage
import info.nightscout.androidaps.plugins.general.tidepool.messages.DatasetReplyMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.powermock.modules.junit4.PowerMockRunner

@RunWith(PowerMockRunner::class)
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