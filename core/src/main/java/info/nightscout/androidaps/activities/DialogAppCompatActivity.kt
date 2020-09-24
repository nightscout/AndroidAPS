package info.nightscout.androidaps.activities

import android.content.Context
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.utils.locale.LocaleHelper

open class DialogAppCompatActivity : DaggerAppCompatActivity() {
    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
