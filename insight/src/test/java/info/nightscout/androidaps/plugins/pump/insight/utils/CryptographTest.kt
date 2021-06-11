package info.nightscout.androidaps.plugins.pump.insight.utils

import info.nightscout.androidaps.TestBase
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.Cryptograph
import info.nightscout.androidaps.plugins.pump.insight.utils.crypto.DerivedKeys
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptographTest : TestBase() {



    @Test
    fun `test deriveKeys function`() { //Todo
        val verificationSeed = byteArrayOf(1,2,3,4) //length 622 !!!
        val secret = byteArrayOf(5,6,7,8)       // length 32
        val random = byteArrayOf(5,6,7,8)       // length 28
        val peerRandom = byteArrayOf(5,6,7,8)   // length 28
        val derivedKeys = DerivedKeys()
        derivedKeys.incomingKey = byteArrayOf(1,2,3,4)
        derivedKeys.outgoingKey = byteArrayOf(1,2,3,4)
        val result = byteArrayOf(1,2,3,4,5,6,7,8)
        assertTrue(Cryptograph.deriveKeys(verificationSeed, secret, random, peerRandom).incomingKey.contentEquals(derivedKeys.incomingKey))
        assertTrue(Cryptograph.deriveKeys(verificationSeed, secret, random, peerRandom).outgoingKey.contentEquals(derivedKeys.outgoingKey))
    }

    @Test
    fun `test decryptRSA function`() { //Todo
        val data = byteArrayOf(1,2,3,4)
        val key = ""

        val result = byteArrayOf(1,2,3,4,5,6,7,8)
        //assertTrue(Cryptograph.decryptRSA(byteArray1, data).contentEquals(result))
    }

    @Test
    fun `test combine function`() {
        val byteArray1 = byteArrayOf(1,2,3,4)
        val byteArray2 = byteArrayOf(5,6,7,8)
        val result = byteArrayOf(1,2,3,4,5,6,7,8)
        val result2 = byteArrayOf(1,2,3,4,5,6,7,8,9)
        assertTrue(Cryptograph.combine(byteArray1, byteArray2).contentEquals(result))
        assertTrue(Cryptograph.combine(byteArray1, byteArray2).contentEquals(result2))
    }

    @Test
    fun `test encryptDataCTR function`() {
        val data = byteArrayOf(32, 0, 11, -16, 0, 0, 8, 1, 0, 25, 96, 0)
        val key = byteArrayOf(84, -14, 48, -84, 81, -97, -112, 64, -125, 60, -62, -39, -84, 71, 24, 50)
        val nonce = byteArrayOf(-39, 96, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val result = byteArrayOf(-10, -113, 58, -37, -80, -89, 65, -63, -121, 5, -105)
        assertTrue(Cryptograph.encryptDataCTR(data, key, nonce).contentEquals(result))
    }

    @Test
    fun `test produceCCMTag function`() {
        val nonce = byteArrayOf(7, 41, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val payload = ByteArray(0)
        val header = byteArrayOf(32, 23, 0, 0, 1, 0, 1, 0, 7, 41, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val key = byteArrayOf(84, -14, 48, -84, 81, -97, -112, 64, -125, 60, -62, -39, -84, 71, 24, 50)
        val result = byteArrayOf(92, -49, -51, 45, 56, 7, 68, 21)
        assertTrue(Cryptograph.produceCCMTag(nonce, payload, header, key).contentEquals(result))
    }

    @Test
    fun `test CRC function`() {
        val byteArray1 = byteArrayOf(-42, 100, 31, 0, -76, 0, 31, 0)
        val byteArray2 = byteArrayOf(0, 0, 31, 0, -112, 29, -29, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        val crc = 9653
        val crc2 = 63483
        assertTrue(Cryptograph.calculateCRC(byteArray1).equals(crc))    // should pass
        assertTrue(Cryptograph.calculateCRC(byteArray2).equals(crc2))   // should pass
        assertTrue(Cryptograph.calculateCRC(byteArray1).equals(crc2))   // should fail to check if test ok then remove
    }

}