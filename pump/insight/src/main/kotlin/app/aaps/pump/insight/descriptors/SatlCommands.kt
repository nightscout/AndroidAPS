@file:Suppress("SpellCheckingInspection")

package app.aaps.pump.insight.descriptors

import app.aaps.pump.insight.satl.ConnectionRequest
import app.aaps.pump.insight.satl.ConnectionResponse
import app.aaps.pump.insight.satl.DataMessage
import app.aaps.pump.insight.satl.DisconnectMessage
import app.aaps.pump.insight.satl.ErrorMessage
import app.aaps.pump.insight.satl.KeyRequest
import app.aaps.pump.insight.satl.KeyResponse
import app.aaps.pump.insight.satl.SatlMessage
import app.aaps.pump.insight.satl.SynAckResponse
import app.aaps.pump.insight.satl.SynRequest
import app.aaps.pump.insight.satl.VerifyConfirmRequest
import app.aaps.pump.insight.satl.VerifyConfirmResponse
import app.aaps.pump.insight.satl.VerifyDisplayRequest
import app.aaps.pump.insight.satl.VerifyDisplayResponse

class SatlCommands(val id: Byte, val type: Class<out SatlMessage?>) {

    companion object {

        fun fromType(type: SatlMessage?): Byte = when (type) {
            is DataMessage           -> DATAMESSAGE
            is ErrorMessage          -> ERRORMESSAGE
            is ConnectionRequest     -> CONNECTIONREQUEST
            is ConnectionResponse    -> CONNECTIONRESPONSE
            is KeyRequest            -> KEYREQUEST
            is VerifyConfirmRequest  -> VERIFYCONFIRMREQUEST
            is KeyResponse           -> KEYRESPONSE
            is VerifyDisplayRequest  -> VERIFYDISPLAYREQUEST
            is VerifyDisplayResponse -> VERIFYDISPLAYRESPONSE
            is SynRequest            -> SYNREQUEST
            is SynAckResponse        -> SYNACKRESPONSE
            is DisconnectMessage     -> DISCONNECTMESSAGE
            is VerifyConfirmResponse -> VERIFYCONFIRMRESPONSE
            else                     -> 0.toByte()
        }

        fun fromId(id: Byte): SatlMessage? = when (id) {
            DATAMESSAGE           -> DataMessage()
            ERRORMESSAGE          -> ErrorMessage()
            CONNECTIONREQUEST     -> ConnectionRequest()
            CONNECTIONRESPONSE    -> ConnectionResponse()
            KEYREQUEST            -> KeyRequest()
            VERIFYCONFIRMREQUEST  -> VerifyConfirmRequest()
            KEYRESPONSE           -> KeyResponse()
            VERIFYDISPLAYREQUEST  -> VerifyDisplayRequest()
            VERIFYDISPLAYRESPONSE -> VerifyDisplayResponse()
            SYNREQUEST            -> SynRequest()
            SYNACKRESPONSE        -> SynAckResponse()
            DISCONNECTMESSAGE     -> DisconnectMessage()
            VERIFYCONFIRMRESPONSE -> VerifyConfirmResponse()
            else                  -> null
        }

        private const val DATAMESSAGE = 3.toByte()
        private const val ERRORMESSAGE = 6.toByte()
        private const val CONNECTIONREQUEST = 9.toByte()
        private const val CONNECTIONRESPONSE = 10.toByte()
        private const val KEYREQUEST = 12.toByte()
        private const val VERIFYCONFIRMREQUEST = 14.toByte()
        private const val KEYRESPONSE = 17.toByte()
        private const val VERIFYDISPLAYREQUEST = 18.toByte()
        private const val VERIFYDISPLAYRESPONSE = 20.toByte()
        private const val SYNREQUEST = 23.toByte()
        private const val SYNACKRESPONSE = 24.toByte()
        private const val DISCONNECTMESSAGE = 27.toByte()
        private const val VERIFYCONFIRMRESPONSE = 30.toByte()
    }
}