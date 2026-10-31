package app.aaps.core.nssdk.exceptions

import kotlinx.io.IOException

/**
 * Base class for everything this client throws.
 *
 * The base is **kotlinx-io**'s `IOException`, not `java.io.IOException`, because the latter is a JVM
 * type and would keep this module off iOS. On the JVM kotlinx-io declares it as
 * `actual typealias IOException = java.io.IOException`, so the two are the *same class* there and
 * existing `catch (e: java.io.IOException)` blocks keep catching - `PairingOfferFetcher` and
 * `PairingOfferPublisher` in `:plugins:sync` rely on that, and `deleteOffer` in particular must
 * never throw, because a pairing offer left on the server keeps a PIN brute-force window open.
 *
 * `NightscoutExceptionTest` asserts the identity per subclass rather than trusting it: a hand rolled
 * `expect class` would compile just as happily and silently turn those catches into non-catches.
 *
 * kotlinx-io arrives transitively with Ktor, so this costs no new dependency.
 */
abstract class NightscoutException(message: String, cause: Throwable? = null) : IOException(message, cause)