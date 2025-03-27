package app.aaps.plugins.sync.tizen

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.Bundle
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.nsclient.ProcessedDeviceStatusData
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.Event
import app.aaps.core.interfaces.rx.events.EventAutosensCalculationFinished
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventOverviewBolusProgress
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.extensions.durationInMinutes
import app.aaps.core.objects.extensions.round
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.plugins.sync.R
import app.aaps.shared.impl.extensions.safeQueryBroadcastReceivers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TizenPlugin @Inject constructor(
    aapsLogger: AAPSLogger,
    rh: ResourceHelper,
    private val aapsSchedulers: AapsSchedulers,
    private val context: Context,
    private val dateUtil: DateUtil,
    private val fabricPrivacy: FabricPrivacy,
    private val rxBus: RxBus,
    private val iobCobCalculator: IobCobCalculator,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val profileFunction: ProfileFunction,
    private val preferences: Preferences,
    private val processedDeviceStatusData: ProcessedDeviceStatusData,
    private val loop: Loop,
    private val activePlugin: ActivePlugin,
    private var receiverStatusStore: ReceiverStatusStore,
    private val config: Config,
    private val glucoseStatusProvider: GlucoseStatusProvider,
) : PluginBase(
    PluginDescription()
        .mainType(PluginType.SYNC)
        .pluginIcon(app.aaps.core.objects.R.drawable.ic_watch)
        .pluginName(R.string.tizen)
        .shortName(R.string.tizen_short)
        .description(R.string.tizen_description),
    aapsLogger, rh
) {

    private val disposable = CompositeDisposable()
    override fun onStart() {
        super.onStart()
        disposable += rxBus
            .toObservable(EventLoopUpdateGui::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventAutosensCalculationFinished::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException)
        disposable += rxBus
            .toObservable(EventOverviewBolusProgress::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe({ sendData(it) }, fabricPrivacy::logException)
    }

    override fun onStop() {
        disposable.clear()
        super.onStop()
    }

    internal fun prepareData(event: Event, bundle: Bundle) {
        bgStatus(bundle)
        iobCob(bundle)
        loopStatus(bundle)
        basalStatus(bundle)
        pumpStatus(bundle)

        if (event is EventOverviewBolusProgress && !event.isSMB()) {
            bundle.putInt("progressPercent", event.percent)
            bundle.putString("progressStatus", event.status)
        }
    }

    private fun sendData(event: Event) {
        val bundle = Bundle()
        prepareData(event, bundle)

        sendBroadcast(
            Intent(Intents.AAPS_BROADCAST) // "info.nightscout.androidaps.status"
                .addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                .putExtras(bundle)
        )
    }

    private fun bgStatus(bundle: Bundle) {
        val lastBG = iobCobCalculator.ads.lastBg() ?: return
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData ?: return

        bundle.putDouble("glucoseMgdl", lastBG.recalculated)   // last BG in mgdl
        bundle.putLong("glucoseTimeStamp", lastBG.timestamp) // timestamp
        bundle.putString("units", profileFunction.getUnits().asText) // units used in AAPS "mg/dl" or "mmol"
        bundle.putString("slopeArrow", lastBG.trendArrow.text) // direction arrow as string
        bundle.putDouble("deltaMgdl", glucoseStatus.delta) // bg delta in mgdl
        bundle.putDouble("avgDeltaMgdl", glucoseStatus.shortAvgDelta) // average bg delta
        bundle.putDouble("high", preferences.get(UnitDoubleKey.OverviewHighMark)) // predefined top value of in range (green area)
        bundle.putDouble("low", preferences.get(UnitDoubleKey.OverviewLowMark)) // predefined bottom  value of in range
    }

    private fun iobCob(bundle: Bundle) {
        profileFunction.getProfile() ?: return
        val bolusIob = iobCobCalculator.calculateIobFromBolus().round()
        val basalIob = iobCobCalculator.calculateIobFromTempBasalsIncludingConvertedExtended().round()
        bundle.putDouble("bolusIob", bolusIob.iob)
        bundle.putDouble("basalIob", basalIob.basaliob)
        bundle.putDouble("iob", bolusIob.iob + basalIob.basaliob) // total IOB

        val cob = iobCobCalculator.getCobInfo("broadcast")
        bundle.putDouble("cob", cob.displayCob ?: -1.0) // COB [g] or -1 if N/A
        bundle.putDouble("futureCarbs", cob.futureCarbs) // future scheduled carbs
    }

    private fun loopStatus(bundle: Bundle) {
        //batteries
        bundle.putInt("phoneBattery", receiverStatusStore.batteryLevel)
        bundle.putInt("rigBattery", processedDeviceStatusData.uploaderStatus.replace("%", "").trim { it <= ' ' }.toInt())

        if (config.APS && loop.lastRun?.lastTBREnact != 0L) { //we are AndroidAPS
            bundle.putLong("suggestedTimeStamp", loop.lastRun?.lastAPSRun ?: -1L)
            bundle.putString("suggested", loop.lastRun?.request?.json().toString())
            if (loop.lastRun?.tbrSetByPump != null && loop.lastRun?.tbrSetByPump?.enacted == true) {
                bundle.putLong("enactedTimeStamp", loop.lastRun?.lastTBREnact ?: -1L)
                bundle.putString("enacted", loop.lastRun?.request?.json().toString())
            }
        } else { //NSClient or remote
            val data = processedDeviceStatusData.openAPSData
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
        bundle.putDouble("baseBasal", profile.getBasal())
        bundle.putString("profile", profileFunction.getProfileName())
        processedTbrEbData.getTempBasalIncludingConvertedExtended(now)?.let {
            bundle.putLong("tempBasalStart", it.timestamp)
            bundle.putLong("tempBasalDurationInMinutes", it.durationInMinutes)
            if (it.isAbsolute) bundle.putDouble("tempBasalAbsolute", it.rate) // U/h for absolute TBR
            else bundle.putInt("tempBasalPercent", it.rate.toInt()) // % for percent type TBR
            bundle.putString("tempBasalString", it.toStringFull(profile, dateUtil, rh)) // user friendly string
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
        val receivers: List<ResolveInfo> = context.packageManager.safeQueryBroadcastReceivers(intent, 0)
        for (resolveInfo in receivers)
            resolveInfo.activityInfo.packageName?.let {
                intent.setPackage(it)
                context.sendBroadcast(intent)
                aapsLogger.debug(LTag.CORE, "Sending broadcast " + intent.action + " to: " + it)
            }
    }
}