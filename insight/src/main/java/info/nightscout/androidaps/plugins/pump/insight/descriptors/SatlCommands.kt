package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.satl.*

class SatlCommands (val id: Byte, val type: Class<out SatlMessage?>)  {

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

        const val DATAMESSAGE = 3.toByte()
        const val ERRORMESSAGE = 6.toByte()
        const val CONNECTIONREQUEST = 9.toByte()
        const val CONNECTIONRESPONSE = 10.toByte()
        const val KEYREQUEST = 12.toByte()
        const val VERIFYCONFIRMREQUEST = 14.toByte()
        const val KEYRESPONSE = 17.toByte()
        const val VERIFYDISPLAYREQUEST = 18.toByte()
        const val VERIFYDISPLAYRESPONSE = 20.toByte()
        const val SYNREQUEST = 23.toByte()
        const val SYNACKRESPONSE = 24.toByte()
        const val DISCONNECTMESSAGE = 27.toByte()
        const val VERIFYCONFIRMRESPONSE = 30.toByte()
    }
}