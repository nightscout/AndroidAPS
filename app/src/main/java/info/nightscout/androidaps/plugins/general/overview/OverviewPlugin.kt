package info.nightscout.androidaps.plugins.general.overview

import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.extensions.plusAssign
import info.nightscout.androidaps.utils.resources.ResourceHelper
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val notificationStore: NotificationStore,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBusWrapper,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(OverviewFragment::class.qualifiedName)
    .alwaysVisible(true)
    .alwaysEnabled(true)
    .pluginName(R.string.overview)
    .shortName(R.string.overview_shortname)
    .preferencesId(R.xml.pref_overview)
    .description(R.string.description_overview),
    aapsLogger, resourceHelper, injector
) {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override fun onStart() {
        super.onStart()
        notificationStore.createNotificationChannel()
        disposable += rxBus
            .toObservable(EventNewNotification::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ n ->
                if (notificationStore.add(n.notification))
                    rxBus.send(EventRefreshOverview("EventNewNotification"))
            }, { fabricPrivacy.logException(it) })
        disposable += rxBus
            .toObservable(EventDismissNotification::class.java)
            .observeOn(Schedulers.io())
            .subscribe({ n ->
                if (notificationStore.remove(n.id))
                    rxBus.send(EventRefreshOverview("EventDismissNotification"))
            }, { fabricPrivacy.logException(it) })
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun preprocessPreferences(preferenceFragment: PreferenceFragmentCompat) {
        super.preprocessPreferences(preferenceFragment)
        if (config.NSCLIENT) {
            (preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_cgm_button)) as SwitchPreference?)?.let {
                it.isVisible = false
                it.isEnabled = false
            }
            (preferenceFragment.findPreference(resourceHelper.gs(R.string.key_show_calibration_button)) as SwitchPreference?)?.let {
                it.isVisible = false
                it.isEnabled = false
            }
        }
    }
}
