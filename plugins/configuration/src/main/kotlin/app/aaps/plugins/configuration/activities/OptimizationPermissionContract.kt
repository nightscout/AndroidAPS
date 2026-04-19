package app.aaps.plugins.configuration.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.net.toUri

class OptimizationPermissionContract : ActivityResultContract<Void?, Unit?>() {

    override fun parseResult(resultCode: Int, intent: Intent?): Unit? = null

    @SuppressLint("BatteryLife")
    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent().also {
            it.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            it.data = ("package:" + context.packageName).toUri()
        }
    }
}