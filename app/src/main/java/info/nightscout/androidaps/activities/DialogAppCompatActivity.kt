package info.nightscout.androidaps.activities

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import info.nightscout.androidaps.utils.LocaleHelper

open class DialogAppCompatActivity : AppCompatActivity() {
    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
