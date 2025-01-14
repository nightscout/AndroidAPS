package app.aaps.plugins.configuration.maintenance

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.FragmentActivity
import app.aaps.plugins.configuration.maintenance.activities.PrefImportListActivity

class PrefsFileContract : ActivityResultContract<Void?, Boolean>() {

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean {
        return when (resultCode) {
            // Do not pass full file through intent. It crash on large file
            // FragmentActivity.RESULT_OK -> intent?.safeGetParcelableExtra(OUTPUT_PARAM, PrefsFile::class.java)
            FragmentActivity.RESULT_OK -> true
            else                       -> false
        }
    }

    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent(context, PrefImportListActivity::class.java)
    }
}