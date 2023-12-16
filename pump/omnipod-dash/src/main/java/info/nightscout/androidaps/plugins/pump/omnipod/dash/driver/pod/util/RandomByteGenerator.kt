package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util

import java.security.SecureRandom

class RandomByteGenerator {

    private val secureRandom = SecureRandom()

    fun nextBytes(length: Int): ByteArray = ByteArray(length).also(secureRandom::nextBytes)
}
