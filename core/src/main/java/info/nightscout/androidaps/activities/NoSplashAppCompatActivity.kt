package info.nightscout.androidaps.activities

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

open class NoSplashAppCompatActivity : DaggerAppCompatActivityWithResult() {
 @Inject lateinit var spSplash: SP
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var themeToSet = spSplash.getInt("theme", ThemeUtil.THEME_DARKSIDE)
        try {
            setTheme(themeToSet)
            val theme = super.getTheme()
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            theme.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if ( spSplash.getBoolean("daynight", true)) {
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
            val cd = ColorDrawable(spSplash.getInt("darkBackgroundColor", ContextCompat.getColor(this, R.color.background_dark)))
            if ( !spSplash.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable(cd)
        } else {
            delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_NO
            val cd = ColorDrawable(spSplash.getInt("lightBackgroundColor", ContextCompat.getColor(this, R.color.background_light)))
            if ( !spSplash.getBoolean("backgroundcolor", true)) window.setBackgroundDrawable( cd)
        }

    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
