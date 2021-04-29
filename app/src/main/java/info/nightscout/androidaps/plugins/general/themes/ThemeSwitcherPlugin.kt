package info.nightscout.androidaps.plugins.general.themes

import android.app.Application
import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.MainActivity
import info.nightscout.androidaps.R

import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.events.EventThemeSwitch
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeSwitcherPlugin @Inject constructor(
    injector: HasAndroidInjector,
    resourceHelper: ResourceHelper,
    aapsLogger: AAPSLogger,
    private val rxBusWrapper: RxBusWrapper,
    private val sp: SP
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .neverVisible(true)
    .alwaysEnabled(true)
    .showInList(false),
    aapsLogger, resourceHelper, injector
) {

    private val compositeDisposable = CompositeDisposable()

    override fun onStart() {
        compositeDisposable.add(rxBusWrapper.toObservable(EventPreferenceChange::class.java).subscribe {
            if (it.isChanged(resourceHelper, id = R.string.key_useDarkmode)) switchTheme()
        })
    }

    private fun switchTheme() {
        when(sp.getString(R.string.key_useDarkmode, "system")) {
            sp.getString(R.string.key_Dark, "system") -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            sp.getString(R.string.key_light, "system") -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
        rxBusWrapper.send(EventThemeSwitch())
    }

    override fun onStop() {
        compositeDisposable.dispose()
    }
}
