package info.nightscout.androidaps.activities

import android.content.Context
import android.os.Bundle
import info.nightscout.androidaps.core.R
import info.nightscout.androidaps.events.EventThemeSwitch
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.locale.LocaleHelper
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject

open class NoSplashAppCompatActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var rxBusWrapper: RxBusWrapper

    private val compositeDisposable = CompositeDisposable()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)

        compositeDisposable.add(rxBusWrapper.toObservable(EventThemeSwitch::class.java).subscribe {
            theme.applyStyle(R.style.CustomTheme,true)
            recreate()
        })
    }

    override fun onDestroy() {
        compositeDisposable.clear()
        super.onDestroy()
    }

    public override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }
}