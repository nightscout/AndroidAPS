package app.aaps.plugins.source.activities

import android.os.Bundle
import app.aaps.plugins.source.DexcomPlugin
import dagger.android.support.DaggerAppCompatActivity

class RequestDexcomPermissionActivity : DaggerAppCompatActivity() {

    private val requestCode = "AndroidAPS <3".map { it.code }.sum()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(DexcomPlugin.PERMISSION), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

}