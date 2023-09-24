package info.nightscout.plugins.general.themes

import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import app.aaps.interfaces.logging.AAPSLogger
import app.aaps.interfaces.plugin.PluginBase
import app.aaps.interfaces.plugin.PluginDescription
import app.aaps.interfaces.plugin.PluginType
import app.aaps.interfaces.resources.ResourceHelper
import app.aaps.interfaces.rx.bus.RxBus
import app.aaps.interfaces.rx.events.EventPreferenceChange
import app.aaps.interfaces.rx.events.EventThemeSwitch
import app.aaps.interfaces.sharedPreferences.SP
import dagger.android.HasAndroidInjector
import info.nightscout.plugins.R
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
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .neverVisible(true)
        .alwaysEnabled(true)
        .showInList(false)
        .pluginName(R.string.theme_switcher),
    aapsLogger, rh, injector
) {

    private val compositeDisposable = CompositeDisposable()

    override fun onStart() {
        compositeDisposable.add(rxBus.toObservable(EventPreferenceChange::class.java).subscribe {
            if (it.isChanged(rh.gs(info.nightscout.core.utils.R.string.key_use_dark_mode))) {
                setThemeMode()
                rxBus.send(EventThemeSwitch())
            }
        })
    }

    fun setThemeMode() {
        val mode = try {
            when (sp.getString(info.nightscout.core.utils.R.string.key_use_dark_mode, "dark")) {
                sp.getString(R.string.value_dark_theme, "dark")   -> MODE_NIGHT_YES
                sp.getString(R.string.value_light_theme, "light") -> MODE_NIGHT_NO
                else                                              -> MODE_NIGHT_FOLLOW_SYSTEM
            }
        } catch (ignored: Exception) {
            MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    override fun onStop() {
        compositeDisposable.dispose()
    }
}
