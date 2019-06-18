package info.nightscout.androidaps.activities

import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import info.nightscout.androidaps.plugins.source.SourceDexcomPlugin

class RequestDexcomPermissionActivity : AppCompatActivity() {

    private val requestCode = "AndroidAPS <3".map { it.toInt() }.sum()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(arrayOf(SourceDexcomPlugin.PERMISSION), requestCode)
        } else {
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        finish()
    }

}