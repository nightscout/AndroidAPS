package app.aaps.core.objects.crypto

/** Android and the desktop share one implementation, because `javax.crypto` is on both. */
actual fun platformCryptoPrimitives(): CryptoPrimitives = JvmCryptoPrimitives()
