package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.util

import app.aaps.annotations.OpenForTesting
import java.security.SecureRandom

@OpenForTesting
class RandomByteGenerator {

    private val secureRandom = SecureRandom()

    fun nextBytes(length: Int): ByteArray = ByteArray(length).also(secureRandom::nextBytes)
}
