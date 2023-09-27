package app.aaps.core.nssdk.remotemodel

internal data class RemoteAuthResponse(val token: String, val iat: Long, val exp: Long)
