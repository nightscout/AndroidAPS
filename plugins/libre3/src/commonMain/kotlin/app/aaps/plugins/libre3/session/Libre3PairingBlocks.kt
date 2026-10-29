package app.aaps.plugins.libre3.session

import app.aaps.plugins.libre3.crypto.Libre3LibAes
import app.aaps.plugins.libre3.crypto.Libre3RuntimeTables

/**
 * Builds the block maker that the pairing messages use.
 *
 * This is **not** the ordinary AES of the phone. The pairing messages are protected by a table
 * driven routine of the sensor maker, and the tables for it ship with the app. Mixing the two
 * planes is a safety rule of this project: the glucose data uses ordinary AES, the pairing does
 * not, and a message built with the wrong one is simply refused by the other side.
 */
object Libre3PairingBlocks {

    fun factory(): Libre3PairingBlockFactory = Libre3PairingBlockFactory { phase5RawKey ->
        Libre3LibAes.blockMaker(phase5RawKey)
    }

    /** True when the tables this needs really ship with the build. */
    val isAvailable: Boolean get() = Libre3RuntimeTables.pairingTablesPresent()
}
