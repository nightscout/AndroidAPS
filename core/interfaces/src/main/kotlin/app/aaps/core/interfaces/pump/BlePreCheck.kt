package app.aaps.core.interfaces.pump

import android.content.Context
import androidx.appcompat.app.AppCompatActivity

interface BlePreCheck {

    fun prerequisitesCheck(activity: AppCompatActivity, additionalPermissions: List<String>? = null): Boolean

    /**
     * Pure check of BLE readiness without UI or permission requests.
     * Attempts to enable Bluetooth if permissions are granted.
     * Returns [BlePreCheckResult] indicating the current state.
     */
    fun checkBleReady(context: Context): BlePreCheckResult

}