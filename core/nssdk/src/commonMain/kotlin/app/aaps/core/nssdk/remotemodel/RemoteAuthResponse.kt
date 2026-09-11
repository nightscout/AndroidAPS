package app.aaps.core.nssdk.remotemodel

import kotlinx.serialization.Serializable

/**
 * All three fields stay non-null and without defaults on purpose: this is the response to a
 * successful token refresh, so a reply missing any of them is not something to carry on from.
 * Gson would have left them null and failed later; kotlinx fails here, which is the better place.
 */
@Serializable
internal data class RemoteAuthResponse(val token: String, val iat: Long, val exp: Long)
