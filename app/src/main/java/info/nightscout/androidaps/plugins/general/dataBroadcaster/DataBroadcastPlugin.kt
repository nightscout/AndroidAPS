package info.nightscout.androidaps.plugins.general.dataBroadcaster

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import dagger.android.HasAndroidInjector
import info.nightscout.androidaps.Config
import info.nightscout.androidaps.R
import info.nightscout.androidaps.data.IobTotal
import info.nightscout.androidaps.events.Event
import info.nightscout.androidaps.events.EventExtendedBolusChange
import info.nightscout.androidaps.events.EventNewBasalProfile
import info.nightscout.androidaps.events.EventTempBasalChange
import info.nightscout.androidaps.events.EventTreatmentChange
import info.nightscout.androidaps.interfaces.*
import info.nightscout.androidaps.logging.AAPSLogger
import info.nightscout.androidaps.logging.LTag
import info.nightscout.androidaps.plugins.aps.events.EventOpenAPSUpdateGui
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.bus.RxBusWrapper
import info.nightscout.androidaps.plugins.general.nsclient.data.NSDeviceStatus
import info.nightscout.androidaps.plugins.general.overview.events.EventOverviewBolusProgress
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.GlucoseStatus
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.IobCobCalculatorPlugin
import info.nightscout.androidaps.plugins.iob.iobCobCalculator.events.EventAutosensCalculationFinished
import info.nightscout.androidaps.receivers.ReceiverStatusStore
import info.nightscout.androidaps.services.Intents
import info.nightscout.androidaps.utils.DefaultValueHelper
import info.nightscout.androidaps.utils.FabricPrivacy
import info.nightscout.androidaps.utils.resources.ResourceHelper
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import io.reactivex.disposables.CompositeDisposable
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataBroadcastPlugin @Inject constructor(
    injector: HasAndroidInjector,
    aapsLogger: AAPSLogger,
    resourceHelper: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val context: Context,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBusWrapper,
    private val iobCobCalculatorPlugin: IobCobCalculatorPlugin,
    private val profileFunction: ProfileFunction,
    private val defaultValueHelper: DefaultValueHelper,
    private val nsDeviceStatus: NSDeviceStatus,
    private val loopPlugin: LoopPlugin,
    private val activePlugin: ActivePluginProvider,
    private var receiverStatusStore: ReceiverStatusStore,
    private val config: Config,
    private val databaseHelper: DatabaseHelperInterface

) : PluginBase(PluginDescription()
    .mainType(PluginType.GENERAL)
    .pluginName(R.string.databroadcaster)
    .alwaysEnabled(true)
    .neverVisible(true)
    .showInList(false),
    aapsLogger, resourceHelper, injector
) {

    private val disposable = CompositeDisposable()
    override fun onStart() {
        super.onStart()
        disposable.add(rxBus
            .toObservable(EventOpenAPSUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventExtendedBolusChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventTreatmentChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventNewBasalProfile::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException))
        disposable.add(rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException))
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    private fun sendData(event: Event) {
        val bundle = Bundle()
        bgStatus(bundle)
        iobCob(bundle)
        loopStatus(bundle)
        basalStatus(bundle)
        pumpStatus(bundle)

        if (event is EventOverviewBolusProgress && !event.isSMB()) {
            bundle.putInt("progressPercent", event.percent)
            bundle.putString("progressStatus", event.status)
        }

        //aapsLogger.debug("Prepared bundle:\n" + BundleLogger.log(bundle))
        sendBroadcast(
            Intent(Intents.AAPS_BROADCAST) // "info.nightscout.androidaps.status"
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(bundle)
        )
    }

    private fun bgStatus(bundle: Bundle) {
        val lastBG = iobCobCalculatorPlugin.lastBg() ?: return
        val glucoseStatus = GlucoseStatus(injector).glucoseStatusData ?: return

        bundle.putDouble("glucoseMgdl", lastBG.value)   // last BG in mgdl
        bundle.putLong("glucoseTimeStamp", lastBG.timestamp) // timestamp
        bundle.putString("units", profileFunction.getUnits()) // units used in AAPS "mg/dl" or "mmol"
        bundle.putString("slopeArrow", lastBG.trendArrow.text) // direction arrow as string
        bundle.putDouble("deltaMgdl", glucoseStatus.delta) // bg delta in mgdl
        bundle.putDouble("avgDeltaMgdl", glucoseStatus.avgDelta) // average bg delta
        bundle.putDouble("high", defaultValueHelper.determineHighLine()) // predefined top value of in range (green area)
        bundle.putDouble("low", defaultValueHelper.determineLowLine()) // predefined bottom  value of in range
    }

    private fun iobCob(bundle: Bundle) {
        profileFunction.getProfile() ?: return
        activePlugin.activeTreatments.updateTotalIOBTreatments()
        val bolusIob: IobTotal = activePlugin.activeTreatments.lastCalculationTreatments.round()
        activePlugin.activeTreatments.updateTotalIOBTempBasals()
        val basalIob: IobTotal = activePlugin.activeTreatments.lastCalculationTempBasals.round()
        bundle.putDouble("bolusIob", bolusIob.iob)
        bundle.putDouble("basalIob", basalIob.basaliob)
        bundle.putDouble("iob", bolusIob.iob + basalIob.basaliob) // total IOB

        val cob = iobCobCalculatorPlugin.getCobInfo(false, "broadcast")
        bundle.putDouble("cob", cob.displayCob ?: -1.0) // COB [g] or -1 if N/A
        bundle.putDouble("futureCarbs", cob.futureCarbs) // future scheduled carbs
    }

    private fun loopStatus(bundle: Bundle) {
        //batteries
        bundle.putInt("phoneBattery", receiverStatusStore.batteryLevel)
        bundle.putInt("rigBattery", nsDeviceStatus.uploaderStatus.replace("%", "").trim { it <= ' ' }.toInt())

        if (config.APS && loopPlugin.lastRun?.lastTBREnact != 0L) { //we are AndroidAPS
            bundle.putLong("suggestedTimeStamp", loopPlugin.lastRun?.lastAPSRun ?: -1L)
            bundle.putString("suggested", loopPlugin.lastRun?.request?.json().toString())
            if (loopPlugin.lastRun?.tbrSetByPump != null && loopPlugin.lastRun?.tbrSetByPump?.enacted == true) {
                bundle.putLong("enactedTimeStamp", loopPlugin.lastRun?.lastTBREnact
                    ?: -1L)
                bundle.putString("enacted", loopPlugin.lastRun?.request?.json().toString())
            }
        } else { //NSClient or remote
            val data = NSDeviceStatus.deviceStatusOpenAPSData
            if (data.clockSuggested != 0L && data.suggested != null) {
                bundle.putLong("suggestedTimeStamp", data.clockSuggested)
                bundle.putString("suggested", data.suggested.toString())
            }
            if (data.clockEnacted != 0L && data.enacted != null) {
                bundle.putLong("enactedTimeStamp", data.clockEnacted)
                bundle.putString("enacted", data.enacted.toString())
            }
        }
    }

    private fun basalStatus(bundle: Bundle) {
        val now = System.currentTimeMillis()
        val profile = profileFunction.getProfile() ?: return
        bundle.putLong("basalTimeStamp", now)
        bundle.putDouble("baseBasal", profile.basal)
        bundle.putString("profile", profileFunction.getProfileName())
        activePlugin.activeTreatments.getTempBasalFromHistory(now)?.let {
            bundle.putLong("tempBasalStart", it.date)
            bundle.putInt("tempBasalDurationInMinutes", it.durationInMinutes)
            if (it.isAbsolute) bundle.putDouble("tempBasalAbsolute", it.absoluteRate) // U/h for absolute TBR
            else bundle.putInt("tempBasalPercent", it.percentRate) // % for percent type TBR
            bundle.putString("tempBasalString", it.toStringFull()) // user friendly string
        }
    }

    private fun pumpStatus(bundle: Bundle) {
        val pump = activePlugin.activePump
        bundle.putLong("pumpTimeStamp", pump.lastDataTime())
        bundle.putInt("pumpBattery", pump.batteryLevel)
        bundle.putDouble("pumpReservoir", pump.reservoirLevel)
        bundle.putString("pumpStatus", pump.shortStatus(false))
    }

    private fun sendBroadcast(intent: Intent) {
        val receivers: List<ResolveInfo> = context.packageManager.queryBroadcastReceivers(intent, 0)
        for (resolveInfo in receivers)
            resolveInfo.activityInfo.packageName?.let {
                intent.setPackage(it)
                context.sendBroadcast(intent)
                aapsLogger.debug(LTag.CORE, "Sending broadcast " + intent.action + " to: " + it)
            }
    }
}