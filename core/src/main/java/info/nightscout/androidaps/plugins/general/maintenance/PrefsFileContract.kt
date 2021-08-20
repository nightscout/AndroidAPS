package info.nightscout.androidaps.plugins.general.maintenance

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.FragmentActivity
import info.nightscout.androidaps.plugins.general.maintenance.activities.PrefImportListActivity

class PrefsFileContract : ActivityResultContract<Void, PrefsFile>() {

    companion object {

        const val OUTPUT_PARAM = "prefs_file"
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PrefsFile? {
        return when (resultCode) {
            FragmentActivity.RESULT_OK -> intent?.getParcelableExtra(OUTPUT_PARAM)
            else                       -> null
        }
    }

    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent(context, PrefImportListActivity::class.java)
    }
}

