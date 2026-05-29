package com.nightscout.eversense.packets

import com.nightscout.eversense.enums.EversenseSecurityType

annotation class EversensePacket(
    /** The request id for the packet */
    val requestId: Byte,

    /** The expected response id for this packet */
    val responseId: Byte,

    /** The expected response id for this packet. Only relevant for 365 packets */
    val typeId: Byte,

    /** The required security protocol */
    val securityType: EversenseSecurityType
)
