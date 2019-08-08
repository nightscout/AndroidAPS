package info.nightscout.androidaps.activities

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import info.nightscout.androidaps.R

open class NoSplashAppCompatActivity : AppCompatActivity() {
    public override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme_NoActionBar)
        super.onCreate(savedInstanceState)
    }
}
