package info.nightscout.core.ui.activities

import android.content.Context
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.core.ui.locale.LocaleHelper

open class DialogAppCompatActivity : DaggerAppCompatActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
