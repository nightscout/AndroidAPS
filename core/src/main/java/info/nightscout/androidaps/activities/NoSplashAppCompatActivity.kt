package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

//@Suppress("registered")
open class NoSplashAppCompatActivity : DaggerAppCompatActivity() {
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
            if ( !spSplash.getBoolean("backgroundcolor", true)) window.setBackgroundDrawableResource(R.color.black)
        } else {
            if ( !spSplash.getBoolean("backgroundcolor", true)) window.setBackgroundDrawableResource(R.color.background_light)
        }
    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
