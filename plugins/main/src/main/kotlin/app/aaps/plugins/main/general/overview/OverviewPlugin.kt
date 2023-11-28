package app.aaps.plugins.main.general.overview

import android.content.Context
import androidx.annotation.StringRes
import app.aaps.core.data.plugin.PluginDescription
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.overview.Overview
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.OverviewMenus
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventDismissNotification
import app.aaps.core.interfaces.rx.events.EventIobCalculationProgress
import app.aaps.core.interfaces.rx.events.EventNewHistoryData
import app.aaps.core.interfaces.rx.events.EventNewNotification
import app.aaps.core.interfaces.rx.events.EventPumpStatusChanged
import app.aaps.core.interfaces.rx.events.EventUpdateOverviewCalcProgress
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.Preferences
import app.aaps.core.keys.StringKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.objects.extensions.put
import app.aaps.core.objects.extensions.putString
import app.aaps.core.objects.extensions.store
import app.aaps.core.objects.extensions.storeBoolean
import app.aaps.core.objects.extensions.storeString
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.overview.notifications.NotificationStore
import app.aaps.plugins.main.general.overview.notifications.NotificationWithAction
import app.aaps.plugins.main.general.overview.notifications.events.EventUpdateOverviewNotification
import app.aaps.shared.impl.rx.bus.RxBusImpl
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverviewPlugin @Inject constructor(
    private val injector: HasAndroidInjector,
    private val notificationStore: NotificationStore,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val sp: SP,
    private val preferences: Preferences,
    aapsLogger: AAPSLogger,
    private val aapsSchedulers: AapsSchedulers,
    rh: ResourceHelper,
    private val overviewData: OverviewData,
    private val overviewMenus: OverviewMenus,
    private val context: Context,
    private val constraintsChecker: ConstraintsChecker
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .fragmentClass(OverviewFragment::class.qualifiedName)
        .alwaysVisible(true)
        .alwaysEnabled(true)
        .simpleModePosition(PluginDescription.Position.TAB)
        .pluginIcon(app.aaps.core.ui.R.drawable.ic_home)
        .pluginName(R.string.overview)
        .shortName(R.string.overview_shortname)
        .preferencesId(R.xml.pref_overview)
        .description(R.string.description_overview),
    aapsLogger, rh
), Overview {

    private var disposable: CompositeDisposable = CompositeDisposable()

    override val overviewBus = RxBusImpl(aapsSchedulers, aapsLogger)

    override fun addNotificationWithDialogResponse(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, title: String, message: String) {
        rxBus.send(
            EventNewNotification(
                NotificationWithAction(injector, id, text, level)
                    .also { n ->
                        n.action(actionButtonId) {
                            n.contextForAction?.let { OKDialog.show(it, title, message, null) }
                        }
                    })
        )
    }

    override fun addNotification(id: Int, text: String, level: Int, @StringRes actionButtonId: Int, action: Runnable) {
        rxBus.send(
            EventNewNotification(
                NotificationWithAction(injector, id, text, level).apply {
                    action(actionButtonId, action)
                })
        )
    }

    override fun dismissNotification(id: Int) {
        rxBus.send(EventDismissNotification(id))
    }

    override fun onStart() {
        super.onStart()
        overviewMenus.loadGraphConfig()
        overviewData.initRange()

        notificationStore.createNotificationChannel()
        disposable += rxBus
            .toObservable(EventNewNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ n ->
                           if (notificationStore.add(n.notification))
                               overviewBus.send(EventUpdateOverviewNotification("EventNewNotification"))
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventDismissNotification::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ n ->
                           if (notificationStore.remove(n.id))
                               overviewBus.send(EventUpdateOverviewNotification("EventDismissNotification"))
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventIobCalculationProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           overviewData.calcProgressPct = it.finalPercent
                           overviewBus.send(EventUpdateOverviewCalcProgress("EventIobCalculationProgress"))
                       }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventPumpStatusChanged::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({
                           overviewData.pumpStatus = it.getStatus(context)
                       }, fabricPrivacy::logException)

    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    override fun configuration(): JSONObject =
        JSONObject()
            .put(StringKey.GeneralUnits, preferences, rh)
            .putString(app.aaps.core.utils.R.string.key_quickwizard, sp, rh)
            .put(IntKey.OverviewEatingSoonDuration, preferences, rh)
            .put(UnitDoubleKey.OverviewEatingSoonTarget, preferences, rh)
            .put(IntKey.OverviewActivityDuration, preferences, rh)
            .put(UnitDoubleKey.OverviewActivityTarget, preferences, rh)
            .put(IntKey.OverviewHypoDuration, preferences, rh)
            .put(UnitDoubleKey.OverviewHypoTarget, preferences, rh)
            .put(UnitDoubleKey.OverviewLowMark, preferences, rh)
            .put(UnitDoubleKey.OverviewHighMark, preferences, rh)
            .put(IntKey.OverviewCageWarning, preferences, rh)
            .put(IntKey.OverviewCageCritical, preferences, rh)
            .put(IntKey.OverviewIageWarning, preferences, rh)
            .put(IntKey.OverviewIageCritical, preferences, rh)
            .put(IntKey.OverviewSageWarning, preferences, rh)
            .put(IntKey.OverviewSageCritical, preferences, rh)
            .put(IntKey.OverviewSbatWarning, preferences, rh)
            .put(IntKey.OverviewSbatCritical, preferences, rh)
            .put(IntKey.OverviewBageWarning, preferences, rh)
            .put(IntKey.OverviewBageCritical, preferences, rh)
            .put(IntKey.OverviewResWarning, preferences, rh)
            .put(IntKey.OverviewResCritical, preferences, rh)
            .put(IntKey.OverviewBattWarning, preferences, rh)
            .put(IntKey.OverviewBattCritical, preferences, rh)
            .put(IntKey.OverviewBolusPercentage, preferences, rh)
            .put(rh.gs(app.aaps.core.utils.R.string.key_used_autosens_on_main_phone), constraintsChecker.isAutosensModeEnabled().value()) // can be disabled by activated DynISF

    override fun applyConfiguration(configuration: JSONObject) {
        val previousUnits = preferences.getIfExists(StringKey.GeneralUnits) ?: "old"
        configuration
            .store(StringKey.GeneralUnits, preferences, rh)
            .storeString(app.aaps.core.utils.R.string.key_quickwizard, sp, rh)
            .store(IntKey.OverviewEatingSoonDuration, preferences, rh)
            .store(UnitDoubleKey.OverviewEatingSoonTarget, preferences, rh)
            .store(IntKey.OverviewActivityDuration, preferences, rh)
            .store(UnitDoubleKey.OverviewActivityTarget, preferences, rh)
            .store(IntKey.OverviewHypoDuration, preferences, rh)
            .store(UnitDoubleKey.OverviewHypoTarget, preferences, rh)
            .store(UnitDoubleKey.OverviewLowMark, preferences, rh)
            .store(UnitDoubleKey.OverviewHighMark, preferences, rh)
            .store(IntKey.OverviewCageWarning, preferences, rh)
            .store(IntKey.OverviewCageCritical, preferences, rh)
            .store(IntKey.OverviewIageWarning, preferences, rh)
            .store(IntKey.OverviewIageCritical, preferences, rh)
            .store(IntKey.OverviewSageWarning, preferences, rh)
            .store(IntKey.OverviewSageCritical, preferences, rh)
            .store(IntKey.OverviewSbatWarning, preferences, rh)
            .store(IntKey.OverviewSbatCritical, preferences, rh)
            .store(IntKey.OverviewBageWarning, preferences, rh)
            .store(IntKey.OverviewBageCritical, preferences, rh)
            .store(IntKey.OverviewResWarning, preferences, rh)
            .store(IntKey.OverviewResCritical, preferences, rh)
            .store(IntKey.OverviewBattWarning, preferences, rh)
            .store(IntKey.OverviewBattCritical, preferences, rh)
            .store(IntKey.OverviewBolusPercentage, preferences, rh)
            .storeBoolean(app.aaps.core.utils.R.string.key_used_autosens_on_main_phone, sp, rh)

        val newUnits = preferences.getIfExists(StringKey.GeneralUnits) ?: "new"
        if (previousUnits != newUnits) {
            overviewData.reset()
            rxBus.send(EventNewHistoryData(0L, reloadBgData = true))
        }
    }
}
