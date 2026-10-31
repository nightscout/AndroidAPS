package app.aaps.core.nssdk.networking

/**
 * Turns what the caller supplies into the base URL the client uses.
 *
 * This is all that is left of the old `NetworkStackBuilder`: the OkHttp client, its disk cache, the
 * two Retrofit instances and the auth interceptor are gone, replaced by [NsKtorClient] and
 * [NsAuth].
 */
internal object NsUrl {

    /**
     * Production callers pass a bare host with an optional sub-path - `NSClientV3Plugin.setClient`
     * strips the scheme first - so `host.com` and `host.com/ns` behave exactly as before.
     *
     * An input that already carries a scheme is used as it stands. That is what lets a unit test
     * point the client at `http://localhost:<port>`, and it also stops a stored `http://host` from
     * turning into `https://http://host/api/`, which is what the old string concatenation produced
     * (the caller only strips `https://`).
     */
    fun toBaseUrl(hostOrUrl: String): String {
        val trimmed = hostOrUrl.trimEnd('/')
        val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
        return "$withScheme/api/"
    }
}
