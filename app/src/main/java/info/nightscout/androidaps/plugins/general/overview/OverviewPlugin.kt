package info.nightscout.androidaps.plugins.general.overview

import info.nightscout.androidaps.MainApp
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.Profile
import info.nightscout.androidaps.events.EventRefreshOverview
import info.nightscout.androidaps.interfaces.PluginBase
import info.nightscout.androidaps.interfaces.PluginDescription
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.logging.L
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.general.overview.events.EventDismissNotification
import info.nightscout.androidaps.plugins.general.overview.events.EventNewNotification
import info.nightscout.androidaps.plugins.general.overview.notifications.NotificationStore
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.plusAssign
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import org.slf4j.LoggerFactory

object OverviewPlugin : PluginBase(PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewFragment::class.qualifiedName)
        .alwaysVisible(true)
        .alwaysEnabled(true)
        .pluginName(R.string.overview)
        .shortName(R.string.overview_shortname)
        .preferencesId(R.xml.pref_overview)
        .description(R.string.description_overview)) {

    private val log = LoggerFactory.getLogger(L.OVERVIEW)
    private var disposable: CompositeDisposable = CompositeDisposable()

    var bgTargetLow = 80.0
    var bgTargetHigh = 180.0

    var notificationStore = NotificationStore()

    override fun onStart() {
        super.onStart()
        disposable += RxBus
                .toObservable(EventNewNotification::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ n ->
                    if (notificationStore.add(n.notification))
                        RxBus.send(EventRefreshOverview("EventNewNotification"))
                }, {
                    FabricPrivacy.logException(it)
                })
        disposable += RxBus
                .toObservable(EventDismissNotification::class.java)
                .observeOn(Schedulers.io())
                .subscribe({ n ->
                    if (notificationStore.remove(n.id))
                        RxBus.send(EventRefreshOverview("EventDismissNotification"))
                }, {
                    FabricPrivacy.logException(it)
                })
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    fun determineHighLine(units: String): Double {
        var highLineSetting = SP.getDouble("high_mark", Profile.fromMgdlToUnits(bgTargetHigh, units))!!
        if (highLineSetting < 1)
            highLineSetting = Profile.fromMgdlToUnits(180.0, units)
        return highLineSetting
    }

    fun determineLowLine(): Double {
        val profile = ProfileFunctions.getInstance().profile ?: return bgTargetLow
        return determineLowLine(profile.units)
    }

    fun determineLowLine(units: String): Double {
        var lowLineSetting = SP.getDouble("low_mark", Profile.fromMgdlToUnits(bgTargetLow, units))!!
        if (lowLineSetting < 1)
            lowLineSetting = Profile.fromMgdlToUnits(76.0, units)
        return lowLineSetting
    }
}
