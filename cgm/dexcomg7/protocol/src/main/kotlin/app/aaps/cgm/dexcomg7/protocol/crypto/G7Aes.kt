package app.aaps.cgm.dexcomg7.protocol.crypto

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object G7Aes {

    /**
     * Dexcom's challenge answer: the 8-byte challenge is repeated to fill one AES block, encrypted with
     * the session key in ECB mode, and the first 8 bytes are the answer. Both sides compute it. A
     * mismatch means the two ends do not share a key.
     */
    fun encryptChallenge(challenge: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 16) { "AES-128 key must be 16 bytes, got ${key.size}" }
        require(challenge.size == 8) { "Challenge must be 8 bytes, got ${challenge.size}" }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(challenge + challenge).copyOf(8)
    }
}
