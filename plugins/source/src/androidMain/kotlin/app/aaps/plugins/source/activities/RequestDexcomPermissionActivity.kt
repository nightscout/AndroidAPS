package app.aaps.plugins.source.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.aaps.plugins.source.DexcomPlugin

// No injected fields, so it needs no dependency injection at all. It only extended a dagger.android
// base class out of habit; the injector that served it was generated and injected nothing.
class RequestDexcomPermissionActivity : AppCompatActivity() {

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