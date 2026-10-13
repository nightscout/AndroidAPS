package app.aaps.pump.omnipod.common.bledriver.pod.util

import java.security.SecureRandom

class RandomByteGenerator {

    private val secureRandom = SecureRandom()

    fun nextBytes(length: Int): ByteArray = ByteArray(length).also(secureRandom::nextBytes)
}
