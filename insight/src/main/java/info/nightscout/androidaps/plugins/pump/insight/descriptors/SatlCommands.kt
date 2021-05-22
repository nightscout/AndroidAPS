package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.satl.*

enum class SatlCommands (val id: Byte, val type: Class<out SatlMessage?>)  {
    DATAMESSAGE (3, DataMessage::class.java),
    ERRORMESSAGE (6, ErrorMessage::class.java),
    CONNECTIONREQUEST (9, ConnectionRequest::class.java),
    CONNECTIONRESPONSE (10, ConnectionResponse::class.java),
    KEYREQUEST (12, KeyRequest::class.java),
    VERIFYCONFIRMREQUEST (14, VerifyConfirmRequest::class.java),
    KEYRESPONSE (17, KeyResponse::class.java),
    VERIFYDISPLAYREQUEST (18, VerifyDisplayRequest::class.java),
    VERIFYDISPLAYRESPONSE (20, VerifyDisplayResponse::class.java),
    SYNREQUEST (23, SynRequest::class.java),
    SYNACKRESPONSE (24, SynAckResponse::class.java),
    DISCONNECTMESSAGE (27, DisconnectMessage::class.java),
    VERIFYCONFIRMRESPONSE (30, VerifyConfirmResponse::class.java);

    companion object {
        fun fromType(type: Class<out SatlMessage?>) = values().first { it.type == type }
        fun fromId(id: Byte) = values().firstOrNull { it.id == id }

    }
}