package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util

import app.aaps.annotations.OpenForTesting
import com.google.crypto.tink.subtle.X25519

@OpenForTesting
class X25519KeyGenerator {

    fun generatePrivateKey(): ByteArray = X25519.generatePrivateKey()
    fun publicFromPrivate(privateKey: ByteArray): ByteArray = X25519.publicFromPrivate(privateKey)
    fun computeSharedSecret(privateKey: ByteArray, publicKey: ByteArray): ByteArray =
        X25519.computeSharedSecret(privateKey, publicKey)
}
