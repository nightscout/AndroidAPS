package info.nightscout.configuration.maintenance

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.FragmentActivity
import info.nightscout.configuration.maintenance.activities.PrefImportListActivity
import info.nightscout.core.utils.extensions.safeGetParcelableExtra
import info.nightscout.interfaces.maintenance.PrefsFile

class PrefsFileContract : ActivityResultContract<Void?, PrefsFile?>() {

    companion object {

        const val OUTPUT_PARAM = "prefs_file"
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PrefsFile? {
        return when (resultCode) {
            FragmentActivity.RESULT_OK -> intent?.safeGetParcelableExtra(OUTPUT_PARAM, PrefsFile::class.java)
            else                       -> null
        }
    }

    override fun createIntent(context: Context, input: Void?): Intent {
        return Intent(context, PrefImportListActivity::class.java)
    }
}