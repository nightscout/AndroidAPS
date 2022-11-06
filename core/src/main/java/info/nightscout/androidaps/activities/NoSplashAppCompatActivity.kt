package info.nightscout.androidaps.activities
import android.content.Context
import android.os.Bundle
import info.nightscout.androidaps.core.R
import info.nightscout.interfaces.locale.LocaleHelper
import info.nightscout.rx.bus.RxBus
import info.nightscout.rx.events.EventThemeSwitch
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject

open class NoSplashAppCompatActivity : DaggerAppCompatActivityWithResult() {

    @Inject lateinit var rxBus: RxBus

    private val compositeDisposable = CompositeDisposable()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme_NoActionBar)
        rh.updateContext(this)

        compositeDisposable.add(rxBus.toObservable(EventThemeSwitch::class.java).subscribe {
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
