package app.aaps.plugins.source.activities

import android.os.Bundle
import app.aaps.plugins.source.EversensePlugin
import dagger.android.support.DaggerAppCompatActivity

class RequestEversensePermissionActivity : DaggerAppCompatActivity() {

    private val requestCode = "AndroidAPS BYOESA".sumOf { it.code }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(EversensePlugin.PERMISSION), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }
}
