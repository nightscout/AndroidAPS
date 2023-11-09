package app.aaps.plugins.configuration.maintenance

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.FragmentActivity

class CustomWatchfaceFileContract : ActivityResultContract<Void?, Unit?>() {

    companion object {

        const val OUTPUT_PARAM = "custom_file"
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Unit? {
        return when (resultCode) {
            FragmentActivity.RESULT_OK -> Unit
            else                       -> null
        }
    }

    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent(context, app.aaps.plugins.configuration.maintenance.activities.CustomWatchfaceImportListActivity::class.java)
    }
}