package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.events.EventThemeSwitch
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.general.themeselector.util.ThemeUtil
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.androidaps.utils.locale.LocaleHelper
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject


open class NoSplashAppCompatActivity : DaggerAppCompatActivityWithResult() {
    @Inject lateinit var spSplash: SP
    @Inject lateinit var rxBus: RxBus

    private val compositeDisposable = CompositeDisposable()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)

        compositeDisposable.add(rxBus.toObservable(EventThemeSwitch::class.java).subscribe {
            var themeToSet = spSplash.getInt("theme", ThemeUtil.THEME_DARKSIDE)
            try {
                setTheme(themeToSet)
                val theme = super.getTheme()
                // https://stackoverflow.com/questions/11562051/change-activitys-theme-programmatically
                theme.applyStyle(ThemeUtil.getThemeId(themeToSet), true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            recreate()
        })

    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}