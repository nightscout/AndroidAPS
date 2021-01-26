package info.nightscout.androidaps.utils.permissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContract

class OptimizationPermissionContract : ActivityResultContract<Void, Unit>() {

    override fun parseResult(resultCode: Int, intent: Intent?): Unit? = null

    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent().also {
            it.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            it.data = Uri.parse("package:" + context.packageName)
        }
    }
}

