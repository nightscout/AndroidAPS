package info.nightscout.androidaps.interaction

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import dagger.android.AndroidInjection
import info.nightscout.androidaps.R
import info.nightscout.rx.logging.AAPSLogger
import info.nightscout.rx.logging.LTag
import preference.WearPreferenceActivity
import javax.inject.Inject

class ConfigurationActivity : WearPreferenceActivity() {

    @Inject lateinit var aapsLogger: AAPSLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        title = "Watchface"
        val configFileName = intent.action
        val resXmlId = resources.getIdentifier(configFileName, "xml", applicationContext.packageName)
        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::onCreate --->> getIntent().getAction() $configFileName")
        aapsLogger.debug(LTag.WEAR, "ConfigurationActivity::onCreate --->> resXmlId $resXmlId")
        addPreferencesFromResource(resXmlId)
        val view = window.decorView as ViewGroup
        removeBackgroundRecursively(view)
        view.background = ContextCompat.getDrawable(this, R.drawable.settings_background)
        view.requestFocus()
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private fun removeBackgroundRecursively(parent: View) {
        if (parent is ViewGroup)
            for (i in 0 until parent.childCount)
                removeBackgroundRecursively(parent.getChildAt(i))
        parent.background = null
    }
}