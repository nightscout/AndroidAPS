package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import dagger.android.support.DaggerAppCompatActivity
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_DARKSIDE
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_PINK
import info.nightscout.androidaps.utils.locale.LocaleHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import javax.inject.Inject

//@Suppress("registered")
open class NoSplashAppCompatActivityApp : DaggerAppCompatActivity() {
    @Inject lateinit var sp: SP
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        var themeToSet = sp.getInt("theme", info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.THEME_DARKSIDE)
        try {
            setTheme(themeToSet)
            val theme = super.getTheme()
            // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
            theme.applyStyle(info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil.getThemeId(themeToSet), true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}
