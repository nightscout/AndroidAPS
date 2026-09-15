package app.aaps.implementation.maintenance.cloud

import app.aaps.core.interfaces.sharedPreferences.KeyValueStore

/**
 * The tokens a Google sign in leaves behind, and where they are kept.
 *
 * Three values, and the third is the one that is easy to get wrong: an access token is short lived,
 * so what is stored is the moment it stops working rather than how long it was good for. Storing the
 * duration would mean recomputing an expiry from a start time nobody wrote down.
 *
 * The refresh token is the valuable one. It does not expire on a timer, so losing it means the user
 * signs in again, and leaking it means somebody else reaches their Drive.
 */
class GoogleTokenStore(private val store: KeyValueStore) {

    var refreshToken: String?
        get() = store.getStringOrNull(REFRESH, null)?.ifEmpty { null }
        set(value) = writeOrRemove(REFRESH, value)

    var accessToken: String?
        get() = store.getStringOrNull(ACCESS, null)?.ifEmpty { null }
        set(value) = writeOrRemove(ACCESS, value)

    /** When [accessToken] stops being usable, as milliseconds since the epoch. */
    var expiresAt: Long
        get() = store.getLong(EXPIRY, 0L)
        set(value) = store.putLong(EXPIRY, value)

    /** The verifier is kept only between asking for a sign in and finishing one. */
    var codeVerifier: String?
        get() = store.getStringOrNull(VERIFIER, null)?.ifEmpty { null }
        set(value) = writeOrRemove(VERIFIER, value)

    /** The value echoed back in the redirect, held so it can be compared with what comes back. */
    var state: String?
        get() = store.getStringOrNull(STATE, null)?.ifEmpty { null }
        set(value) = writeOrRemove(STATE, value)

    /**
     * Whether the stored access token is worth trying.
     *
     * [skewMs] is subtracted so a token that expires while the request is in flight is refreshed
     * first rather than being sent and refused.
     */
    fun accessTokenUsableAt(now: Long, skewMs: Long = DEFAULT_SKEW_MS): Boolean =
        accessToken != null && now < expiresAt - skewMs

    /** Signed out. The refresh token goes first, since it is the one that still works. */
    fun clear() {
        listOf(REFRESH, ACCESS, EXPIRY, VERIFIER, STATE).forEach { store.remove(it) }
    }

    private fun writeOrRemove(key: String, value: String?) {
        if (value.isNullOrEmpty()) store.remove(key) else store.putString(key, value)
    }

    private companion object {

        // The names Android already writes, so a phone and an iPhone read the same stored sign in.
        private const val REFRESH = "google_drive_refresh_token"
        private const val ACCESS = "google_drive_access_token"
        private const val EXPIRY = "google_drive_token_expiry"
        private const val VERIFIER = "google_drive_code_verifier"
        private const val STATE = "google_drive_oauth_state"

        /** A minute, so a token is never sent with seconds left on it. */
        private const val DEFAULT_SKEW_MS = 60_000L
    }
}

/** Why a token could not be had, when the difference changes what the user should be told. */
sealed interface TokenFailure {

    /** Nobody has signed in on this device yet. */
    data object NotSignedIn : TokenFailure

    /** The refresh token no longer works - revoked, or the password changed. Signing in again fixes it. */
    data object SignInExpired : TokenFailure

    /** The network, or Google, or us. Trying again later may work. */
    data class Failed(val message: String) : TokenFailure
}
