package com.nightscout.eversense.models

import kotlinx.serialization.Serializable

@Serializable
class EversenseSecureState {
    var canUseShortcut: Boolean = false
    var username: String = ""
    var password: String = ""
    var clientId: String = ""
    var privateKey: String = ""
    var publicKey: String = ""
}
