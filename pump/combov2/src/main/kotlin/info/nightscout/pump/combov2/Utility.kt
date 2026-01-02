package info.nightscout.pump.combov2

import android.content.Context
import app.aaps.core.interfaces.androidPermissions.AndroidPermission
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import info.nightscout.comboctl.android.AndroidBluetoothPermissionException
import info.nightscout.comboctl.base.ComboException
import info.nightscout.comboctl.main.BasalProfile
import info.nightscout.comboctl.main.NUM_COMBO_BASAL_PROFILE_FACTORS
import kotlinx.coroutines.delay
import app.aaps.core.interfaces.profile.Profile as AAPSProfile

// Utility extension functions for clearer conversion between
// ComboCtl units and AAPS units. ComboCtl uses integer-encoded
// decimals. For basal values, the last 3 digits of an integer
// make up the decimal, so for example, 1568 actually means
// 1.568 IU, and 419 means 0.419 IU etc. Similarly, for bolus
// values, the last digit makes up the decimal. 57 means 5.7 IU,
// 4 means 0.4 IU etc.
// To emphasize better that such a conversion is taking place,
// these extension functions are put in place.
internal fun Int.cctlBasalToIU() = this.toDouble() / 1000.0
internal fun Int.cctlBolusToIU() = this.toDouble() / 10.0
internal fun Double.iuToCctlBolus() = (this * 10.0).toInt()

fun AAPSProfile.toComboCtlBasalProfile(): BasalProfile {
    val factors = IntRange(0, NUM_COMBO_BASAL_PROFILE_FACTORS - 1).map { hour ->
        (this.getBasalTimeFromMidnight(hour * 60 * 60) * 1000.0).toInt()
    }
    return BasalProfile(factors)
}

internal class RetryPermissionCheckException : ComboException("retry permission check")

// Utility function to perform Android permission checks before running a block.
// If the permissions are not given, wait for a little while, then retry.
// This is needed for AAPS<->combov2 integration, since AAPS can start combov2
// even _before_ the user granted AAPS BLUETOOTH_CONNECT etc. permissions.
//
// permissionsToCheckFor is a collection of permissions strings like
// Manifest.permission.BLUETOOTH_SCAN. The function goes through the collection,
// and checks each and every permission to see if they have all been granted.
// Only if all have been granted will the block be executed.
//
// It is possible that within the block, some additional permission checks
// are performed - in particular, these can be checks for permissions that
// weren't part of the permissionsToCheckFor collection. If such a permission
// is not granted, the block can throw AndroidBluetoothPermissionException.
// That exception also specifies what exact permissions haven't been granted
// (yet). runWithPermissionCheck() then adds these missing permissions to
// permissionsToCheckFor, and tries its permission check again, this time
// with these extra permissions included. That way, a failed permission
// check within the block does not break anything, and instead, these
// permissions too are re-checked by the same logic as the one that looks
// at the initially specified permissions.
//
// Additionally, the block might perform other checks that are not directly
// permissions but related to them. One example is a check to see if the
// Bluetooth adapter is enabled in addition to checking for Bluetooth
// permissions. When such custom checks fail, they can throw
// RetryPermissionCheckException to inform this function that it should
// retry its run, just as if a permission hadn't been granted.
internal suspend fun <T> runWithPermissionCheck(
    context: Context,
    config: Config,
    aapsLogger: AAPSLogger,
    androidPermission: AndroidPermission,
    permissionsToCheckFor: Collection<String>,
    block: suspend () -> T
): T {
    var permissions = permissionsToCheckFor
    while (true) {
        try {
            if (config.PUMPDRIVERS) {
                val notAllPermissionsGranted = permissions.fold(initial = false) { currentResult, permission ->
                    return@fold if (androidPermission.permissionNotGranted(context, permission)) {
                        aapsLogger.debug(LTag.PUMP, "permission $permission was not granted by the user")
                        true
                    } else
                        currentResult
                }

                if (notAllPermissionsGranted) {
                    // Wait a little bit before retrying to avoid 100% CPU usage.
                    // It is currently unknown if there is a way to instead get
                    // some sort of event from Android to inform that the permissions
                    // have now been granted, so we have to resort to polling,
                    // at least for now.
                    delay(1000)
                    continue
                }
            }

            return block.invoke()
        } catch (_: RetryPermissionCheckException) {
            // See the above delay() call, which fulfills the exact same purpose.
            delay(1000)
        } catch (e: AndroidBluetoothPermissionException) {
            permissions = permissionsToCheckFor union e.missingPermissions
        }
    }
}
