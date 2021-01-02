package info.nightscout.androidaps.plugins.general.overview

import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.OverviewInterface
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
import info.nightscout.androidaps.utils.extensions.*
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.sharedPreferences.SP
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewPlugin @Inject constructor(
    injector: HasAndroidInjector,
    private val notificationStore: NotificationStore,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBusWrapper,
    private val sp: SP,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val config: Config
) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .fragmentClass(OverviewFragment::class.qualifiedName)
    .alwaysVisible(true)
    .alwaysEnabled(true)
    .pluginIcon(R.drawable.ic_home)
    .pluginName(R.string.overview)
    .shortName(R.string.overview_shortname)
    .preferencesId(R.xml.pref_overview)
    .description(R.string.description_overview),
    aapsLogger, resourceHelper, injector
), OverviewInterface {

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

    override fun configuration(): JSONObject =
        JSONObject()
            .putString(R.string.key_quickwizard, sp, resourceHelper)
            .putInt(R.string.key_eatingsoon_duration, sp, resourceHelper)
            .putDouble(R.string.key_eatingsoon_target, sp, resourceHelper)
            .putInt(R.string.key_activity_duration, sp, resourceHelper)
            .putDouble(R.string.key_activity_target, sp, resourceHelper)
            .putInt(R.string.key_hypo_duration, sp, resourceHelper)
            .putDouble(R.string.key_hypo_target, sp, resourceHelper)
            .putDouble(R.string.key_low_mark, sp, resourceHelper)
            .putDouble(R.string.key_high_mark, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_cage_warning, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_cage_critical, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_iage_warning, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_iage_critical, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_sage_warning, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_sage_critical, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_sbat_warning, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_sbat_critical, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_bage_warning, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_bage_critical, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_res_warning, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_res_critical, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_bat_warning, sp, resourceHelper)
            .putDouble(R.string.key_statuslights_bat_critical, sp, resourceHelper)

    override fun applyConfiguration(configuration: JSONObject) {
        configuration
            .storeString(R.string.key_quickwizard, sp, resourceHelper)
            .storeInt(R.string.key_eatingsoon_duration, sp, resourceHelper)
            .storeDouble(R.string.key_eatingsoon_target, sp, resourceHelper)
            .storeInt(R.string.key_activity_duration, sp, resourceHelper)
            .storeDouble(R.string.key_activity_target, sp, resourceHelper)
            .storeInt(R.string.key_hypo_duration, sp, resourceHelper)
            .storeDouble(R.string.key_hypo_target, sp, resourceHelper)
            .storeDouble(R.string.key_low_mark, sp, resourceHelper)
            .storeDouble(R.string.key_high_mark, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_cage_warning, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_cage_critical, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_iage_warning, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_iage_critical, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_sage_warning, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_sage_critical, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_sbat_warning, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_sbat_critical, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_bage_warning, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_bage_critical, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_res_warning, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_res_critical, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_bat_warning, sp, resourceHelper)
            .storeDouble(R.string.key_statuslights_bat_critical, sp, resourceHelper)
    }
}
