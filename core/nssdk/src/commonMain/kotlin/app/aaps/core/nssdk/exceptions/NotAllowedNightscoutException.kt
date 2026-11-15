package app.aaps.core.nssdk.exceptions

/**
 * The token is valid, but it does not have the right for this call. Nightscout answers 401 for that on API v2 (also after
 * a successful login, see `NsAuth`) and 403 on API v3.
 */
class NotAllowedNightscoutException(message: String) : NightscoutException(message)
