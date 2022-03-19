package info.nightscout.androidaps.plugins.general.themes

import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventPreferenceChange
import info.nightscout.androidaps.events.EventThemeSwitch
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.shared.sharedPreferences.SP
import io.reactivex.rxjava3.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeSwitcherPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val sp: SP,
    private val rxBus: RxBus,
) : PluginBase(PluginDescription()
                   .mainType(PluginType.GENERAL)
                   .neverVisible(true)
                   .alwaysEnabled(true)
                   .showInList(false)
                   .pluginName(R.string.dst_plugin_name),
               aapsLogger, rh, injector
) {

    private val compositeDisposable = CompositeDisposable()

    override fun onStart() {
        compositeDisposable.add(rxBus.toObservable(EventPreferenceChange::class.java).subscribe {
            if (it.isChanged(rh, id = R.string.key_use_dark_mode)) {
                setThemeMode()
                rxBus.send(EventThemeSwitch())
            }
        })
    }

    fun setThemeMode() {
        val mode = when (sp.getString(R.string.key_use_dark_mode, "dark")) {
            sp.getString(R.string.value_dark_theme, "dark")   -> MODE_NIGHT_YES
            sp.getString(R.string.value_light_theme, "light") -> MODE_NIGHT_NO
            else                                              -> MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onStop() {
        compositeDisposable.dispose()
    }
}
