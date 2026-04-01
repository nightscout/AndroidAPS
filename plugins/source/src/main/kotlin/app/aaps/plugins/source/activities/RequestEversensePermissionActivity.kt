package app.aaps.plugins.source.activities

import android.Manifest
import android.os.Build
import android.os.Bundle
import app.aaps.core.ui.activities.TranslatedDaggerAppCompatActivity

class RequestEversensePermissionActivity : TranslatedDaggerAppCompatActivity() {

    private val requestCode = "EversenseBLE".map { it.code }.sum()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // BLUETOOTH_CONNECT and BLUETOOTH_SCAN are runtime permissions on Android 12+ (API 31+).
        // On older versions BLUETOOTH is sufficient and is a normal (auto-granted) permission.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                ),
                requestCode
            )
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.BLUETOOTH),
                requestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}