package app.aaps.plugins.automation

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Always empty: iOS does not let an app read the phone's paired Bluetooth devices.
 *
 * Android answers this from the Bluetooth adapter's bonded device list. iOS has no counterpart -
 * CoreBluetooth only ever shows peripherals **this app** has connected to, never what the phone is
 * paired with, and no permission changes that.
 *
 * The interface distinguishes null ("not allowed, the user can fix it") from an empty list ("the
 * phone really has no paired device"). Neither is quite true here, and an empty list is the less
 * misleading of the two: null would send the user looking for a permission that does not exist. The
 * log line says what actually happened, so a device picker that stays empty on iOS can be explained
 * rather than guessed at.
 */
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class IosPairedBtDevices @Inject constructor(
    private val aapsLogger: AAPSLogger
) : PairedBtDevices {

    override fun names(): List<String> {
        aapsLogger.debug(LTag.AUTOMATION, "Paired Bluetooth devices cannot be read on iOS, returning none")
        return emptyList()
    }
}
