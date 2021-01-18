package info.nightscout.androidaps.activities

import android.os.Bundle
import info.nightscout.androidaps.plugins.source.DexcomPlugin
import javax.inject.Inject

class RequestDexcomPermissionActivity : DialogAppCompatActivity() {
    @Inject lateinit var dexcomPlugin: DexcomPlugin

    private val requestCode = "AndroidAPS <3".map { it.toInt() }.sum()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissions(arrayOf(DexcomPlugin.PERMISSION), requestCode)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        finish()
    }

}