package app.aaps.core.objects.crypto

/**
 * The [CryptoPrimitives] of the platform this build runs on.
 *
 * The implementations differ per platform - `javax.crypto` on Android and the desktop, Apple's
 * CommonCrypto and CryptoKit on iOS - but nothing above this line should have to know which one it
 * got. Keeping the choice here also means the shared tests can name one function and still run
 * against every implementation.
 */
expect fun platformCryptoPrimitives(): CryptoPrimitives
