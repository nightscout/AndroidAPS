package info.nightscout.androidaps.activities

import android.os.Bundle
import info.nightscout.androidaps.plugins.source.SourceDexcomPlugin

class RequestDexcomPermissionActivity : DialogAppCompatActivity() {

    private val requestCode = "AndroidAPS <3".map { it.toInt() }.sum()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(SourceDexcomPlugin.PERMISSION), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        finish()
    }

}