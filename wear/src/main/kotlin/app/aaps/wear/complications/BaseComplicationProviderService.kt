@file:Suppress("DEPRECATION")

package app.aaps.wear.complications

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationManager
import android.support.wearable.complications.ComplicationProviderService
import android.support.wearable.complications.ComplicationText
import android.support.wearable.complications.ProviderUpdateRequester
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionResendData
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear.Companion.INTENT_NEW_DATA
import app.aaps.wear.complications.ComplicationTapBroadcastReceiver.Companion.getTapActionIntent
import app.aaps.wear.complications.ComplicationTapBroadcastReceiver.Companion.getTapWarningSinceIntent
import app.aaps.wear.data.RawDisplayData
import app.aaps.wear.interaction.utils.Constants
import app.aaps.wear.interaction.utils.DisplayFormat
import app.aaps.wear.interaction.utils.Inevitable
import app.aaps.wear.interaction.utils.Persistence
import app.aaps.wear.interaction.utils.WearUtil
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * Base class for all complications
 *
 *
 * Created by dlvoy on 2019-11-12
 */
abstract class BaseComplicationProviderService : ComplicationProviderService() {

    @Inject lateinit var inevitable: Inevitable
    @Inject lateinit var wearUtil: WearUtil
    @Inject lateinit var displayFormat: DisplayFormat
    @Inject lateinit var persistence: Persistence
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus

    // Not derived from DaggerService, do injection here
    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    private var localBroadcastManager: LocalBroadcastManager? = null
    private var messageReceiver: MessageReceiver? = null

    //==============================================================================================
    // ABSTRACT COMPLICATION INTERFACE
    //==============================================================================================
    abstract fun buildComplicationData(dataType: Int, raw: RawDisplayData, complicationPendingIntent: PendingIntent): ComplicationData?
    abstract fun getProviderCanonicalName(): String
    open fun getComplicationAction(): ComplicationAction = ComplicationAction.MENU

    //----------------------------------------------------------------------------------------------
    // DEFAULT BEHAVIOURS
    //----------------------------------------------------------------------------------------------
    private fun buildNoSyncComplicationData(
        dataType: Int,
        raw: RawDisplayData,
        complicationPendingIntent: PendingIntent,
        exceptionalPendingIntent: PendingIntent,
        since: Long
    ): ComplicationData? {
        val builder = ComplicationData.Builder(dataType)
        if (dataType != ComplicationData.TYPE_LARGE_IMAGE) {
            builder.setIcon(Icon.createWithResource(this, R.drawable.ic_sync_alert))
        }
        if (dataType == ComplicationData.TYPE_RANGED_VALUE) {
            builder.setMinValue(0f)
            builder.setMaxValue(100f)
            builder.setValue(0f)
        }
        when (dataType) {
            ComplicationData.TYPE_ICON, ComplicationData.TYPE_SHORT_TEXT, ComplicationData.TYPE_RANGED_VALUE -> if (since > 0) {
                builder.setShortText(ComplicationText.plainText(displayFormat.shortTimeSince(since) + " " + getString(R.string.old)))
            } else {
                builder.setShortText(ComplicationText.plainText(getString(R.string.error)))
            }

            ComplicationData.TYPE_LONG_TEXT                                                                  -> {
                builder.setLongTitle(ComplicationText.plainText(getString(R.string.label_warning_sync)))
                if (since > 0) {
                    builder.setLongText(ComplicationText.plainText(String.format(getString(R.string.label_warning_since), displayFormat.shortTimeSince(since))))
                } else {
                    builder.setLongText(ComplicationText.plainText(getString(R.string.label_warning_sync_aaps)))
                }
            }

            ComplicationData.TYPE_LARGE_IMAGE                                                                -> return buildComplicationData(dataType, raw, complicationPendingIntent)
            else                                                                                             -> aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        builder.setTapAction(exceptionalPendingIntent)
        return builder.build()
    }

    private fun buildOutdatedComplicationData(
        dataType: Int,
        raw: RawDisplayData,
        complicationPendingIntent: PendingIntent,
        exceptionalPendingIntent: PendingIntent,
        since: Long
    ): ComplicationData? {
        val builder = ComplicationData.Builder(dataType)
        if (dataType != ComplicationData.TYPE_LARGE_IMAGE) {
            builder.setIcon(Icon.createWithResource(this, R.drawable.ic_alert))
            builder.setBurnInProtectionIcon(Icon.createWithResource(this, R.drawable.ic_alert_burnin))
        }
        if (dataType == ComplicationData.TYPE_RANGED_VALUE) {
            builder.setMinValue(0f)
            builder.setMaxValue(100f)
            builder.setValue(0f)
        }
        when (dataType) {
            ComplicationData.TYPE_ICON, ComplicationData.TYPE_SHORT_TEXT, ComplicationData.TYPE_RANGED_VALUE -> if (since > 0) {
                builder.setShortText(ComplicationText.plainText(displayFormat.shortTimeSince(since) + " " + getString(R.string.old)))
            } else {
                builder.setShortText(ComplicationText.plainText(getString(R.string.old_warning)))
            }

            ComplicationData.TYPE_LONG_TEXT                                                                  -> {
                builder.setLongTitle(ComplicationText.plainText(getString(R.string.label_warning_old)))
                if (since > 0) {
                    builder.setLongText(ComplicationText.plainText(String.format(getString(R.string.label_warning_since), displayFormat.shortTimeSince(since))))
                } else {
                    builder.setLongText(ComplicationText.plainText(getString(R.string.label_warning_sync_aaps)))
                }
            }

            ComplicationData.TYPE_LARGE_IMAGE                                                                -> return buildComplicationData(dataType, raw, complicationPendingIntent)
            else                                                                                             -> aapsLogger.warn(LTag.WEAR, "Unexpected complication type $dataType")
        }
        builder.setTapAction(exceptionalPendingIntent)
        return builder.build()
    }

    /**
     * If Complication depend on "since" field and need to be updated every minute or not
     * and need only update when new DisplayRawData arrive
     */
    protected open fun usesSinceField(): Boolean {
        return false
    }

    //==============================================================================================
    // COMPLICATION LIFECYCLE
    //==============================================================================================
    /*
     * Called when a complication has been activated. The method is for any one-time
     * (per complication) set-up.
     *
     * You can continue sending data for the active complicationId until onComplicationDeactivated()
     * is called.
     */
    override fun onComplicationActivated(
        complicationId: Int, dataType: Int, complicationManager: ComplicationManager
    ) {
        aapsLogger.warn(LTag.WEAR, "onComplicationActivated(): $complicationId of kind: ${getProviderCanonicalName()}")
        persistence.putString("complication_$complicationId", getProviderCanonicalName())
        persistence.putBoolean("complication_" + complicationId + "_since", usesSinceField())
        persistence.addToSet(Persistence.KEY_COMPLICATIONS, "complication_$complicationId")
        val messageFilter = IntentFilter(INTENT_NEW_DATA)
        messageReceiver = MessageReceiver()
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        messageReceiver?.let { localBroadcastManager?.registerReceiver(it, messageFilter) }
        rxBus.send(EventWearToMobile(ActionResendData("BaseComplicationProviderService")))
        checkIfUpdateNeeded()
    }

    /*
     * Called when the complication needs updated data from your provider. There are four scenarios
     * when this will happen:
     *
     *   1. An active watch face complication is changed to use this provider
     *   2. A complication using this provider becomes active
     *   3. The period of time you specified in the manifest has elapsed (UPDATE_PERIOD_SECONDS)
     *   4. You triggered an update from your own class via the
     *       ProviderUpdateRequester.requestUpdate() method.
     */
    override fun onComplicationUpdate(
        complicationId: Int, dataType: Int, complicationManager: ComplicationManager
    ) {
        aapsLogger.warn(LTag.WEAR, "onComplicationUpdate() id: $complicationId of class: ${getProviderCanonicalName()}")

        // Create Tap Action so that the user can checkIfUpdateNeeded an update by tapping the complication.
        val thisProvider = ComponentName(this, getProviderCanonicalName())

        // We pass the complication id, so we can only update the specific complication tapped.
        val complicationPendingIntent = getTapActionIntent(applicationContext, thisProvider, complicationId, getComplicationAction())
        val raw = RawDisplayData()
        raw.updateFromPersistence(persistence)
        aapsLogger.warn(LTag.WEAR, "Complication data: " + raw.toDebugString())

        // store what is currently rendered in 'SGV since' field, to detect if it was changed and need update
        persistence.putString(
            Persistence.KEY_LAST_SHOWN_SINCE_VALUE,
            displayFormat.shortTimeSince(raw.singleBg[0].timeStamp)
        )

        // by each render we clear stale flag to ensure it is re-rendered at next refresh detection round
        persistence.putBoolean(Persistence.KEY_STALE_REPORTED, false)
        val complicationData: ComplicationData? = when {
            wearUtil.msSince(persistence.whenDataUpdated()) > Constants.STALE_MS -> {
                // no new data arrived - probably configuration or connection error
                val infoToast = getTapWarningSinceIntent(
                    applicationContext, thisProvider, complicationId, ComplicationAction.WARNING_SYNC, persistence.whenDataUpdated()
                )
                buildNoSyncComplicationData(dataType, raw, complicationPendingIntent, infoToast, persistence.whenDataUpdated())
            }

            wearUtil.msSince(raw.singleBg[0].timeStamp) > Constants.STALE_MS     -> {
                // data arriving from phone AAPS, but it is outdated (uploader/NS/xDrip/Sensor error)
                val infoToast = getTapWarningSinceIntent(
                    applicationContext, thisProvider, complicationId, ComplicationAction.WARNING_OLD, raw.singleBg[0].timeStamp
                )
                buildOutdatedComplicationData(dataType, raw, complicationPendingIntent, infoToast, raw.singleBg[0].timeStamp)
            }

            else                                                                 -> {
                // data is up-to-date, we can render standard complication
                buildComplicationData(dataType, raw, complicationPendingIntent)
            }
        }
        if (complicationData != null) {
            complicationManager.updateComplicationData(complicationId, complicationData)
        } else {
            // If no data is sent, we still need to inform the ComplicationManager, so the update
            // job can finish and the wake lock isn't held any longer than necessary.
            complicationManager.noUpdateRequired(complicationId)
        }
    }

    /*
     * Called when the complication has been deactivated.
     */
    override fun onComplicationDeactivated(complicationId: Int) {
        aapsLogger.warn(LTag.WEAR, "onComplicationDeactivated(): $complicationId")
        persistence.removeFromSet(Persistence.KEY_COMPLICATIONS, "complication_$complicationId")
        messageReceiver?.let { localBroadcastManager?.unregisterReceiver(it) }
        inevitable.kill(TASK_ID_REFRESH_COMPLICATION)
    }

    //==============================================================================================
    // UPDATE AND REFRESH LOGIC
    //==============================================================================================
    /*
     * Schedule check for field update
     */
    private fun checkIfUpdateNeeded() {
        aapsLogger.warn(LTag.WEAR, "Pending check if update needed - " + persistence.getString(Persistence.KEY_COMPLICATIONS, ""))
        inevitable.task(TASK_ID_REFRESH_COMPLICATION, 15 * Constants.SECOND_IN_MS) {
            if (wearUtil.isBelowRateLimit("complication-checkIfUpdateNeeded", 5)) {
                aapsLogger.warn(LTag.WEAR, "Checking if update needed")
                requestUpdateIfSinceChanged()
                // We reschedule need for check - to make sure next check will Inevitable go in next 15s
                checkIfUpdateNeeded()
            }
        }
    }

    /*
     * Check if displayed since field (field that shows how old, in minutes, is reading)
     * is up-to-date or need to be changed (a minute or more elapsed)
     */
    private fun requestUpdateIfSinceChanged() {
        val raw = RawDisplayData()
        raw.updateFromPersistence(persistence)
        val lastSince = persistence.getString(Persistence.KEY_LAST_SHOWN_SINCE_VALUE, "-")
        val calcSince = displayFormat.shortTimeSince(raw.singleBg[0].timeStamp)
        val isStale = (wearUtil.msSince(persistence.whenDataUpdated()) > Constants.STALE_MS
            || wearUtil.msSince(raw.singleBg[0].timeStamp) > Constants.STALE_MS)
        val staleWasRefreshed = persistence.getBoolean(Persistence.KEY_STALE_REPORTED, false)
        val sinceWasChanged = lastSince != calcSince
        if (sinceWasChanged || isStale && !staleWasRefreshed) {
            persistence.putString(Persistence.KEY_LAST_SHOWN_SINCE_VALUE, calcSince)
            persistence.putBoolean(Persistence.KEY_STALE_REPORTED, isStale)
            aapsLogger.warn(
                LTag.WEAR, "Detected refresh of time needed! Reason: "
                    + (if (isStale) "- stale detected" else "")
                    + if (sinceWasChanged) "- since changed from: $lastSince to: $calcSince" else ""
            )
            if (isStale) {
                // all complications should update to show offline/old warning
                requestUpdate(activeProviderClasses)
            } else {
                // ... but only some require update due to 'since' field change
                requestUpdate(sinceDependingProviderClasses)
            }
        }
    }

    /*
     * Request update for specified list of providers
     */
    private fun requestUpdate(providers: Set<String>) {
        for (provider in providers) {
            aapsLogger.warn(LTag.WEAR, "Pending update of $provider")
            // We wait with updating allowing all request, from various sources, to arrive
            inevitable.task("update-req-$provider", 700) {
                if (wearUtil.isBelowRateLimit("update-req-$provider", 2)) {
                    aapsLogger.warn(LTag.WEAR, "Requesting update of $provider")
                    val componentName = ComponentName(applicationContext, provider)
                    val providerUpdateRequester = ProviderUpdateRequester(applicationContext, componentName)
                    providerUpdateRequester.requestUpdateAll()
                }
            }
        }
    }

    /*
     * List all Complication providing classes that have active (registered) providers
     */
    private val activeProviderClasses: Set<String>
        get() {
            val providers: MutableSet<String> = HashSet()
            val complications = persistence.getSetOf(Persistence.KEY_COMPLICATIONS)
            for (complication in complications) {
                val providerClass = persistence.getString(complication, "")
                if (providerClass.isNotEmpty()) {
                    providers.add(providerClass)
                }
            }
            return providers
        }

    /*
     * List all Complication providing classes that have active (registered) providers
     * and additionally they depend on "since" field
     *    == they need to be updated not only on data broadcasts, but every minute or so
     */
    private val sinceDependingProviderClasses: Set<String>
        get() {
            val providers: MutableSet<String> = HashSet()
            val complications = persistence.getSetOf(Persistence.KEY_COMPLICATIONS)
            for (complication in complications) {
                val providerClass = persistence.getString(complication, "")
                val dependOnSince = persistence.getBoolean(complication + "_since", false)
                if (providerClass.isNotEmpty() && dependOnSince) {
                    providers.add(providerClass)
                }
            }
            return providers
        }

    /*
     * Listen to broadcast --> new data was stored by ListenerService to Persistence
     */
    inner class MessageReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            updateAll()
        }
    }

    private fun updateAll() {
        val complications = persistence.getSetOf(Persistence.KEY_COMPLICATIONS)
        if (complications.isNotEmpty()) {
            checkIfUpdateNeeded()
            // We request all active providers
            requestUpdate(activeProviderClasses)
        }
    }

    companion object {

        private const val TASK_ID_REFRESH_COMPLICATION = "refresh-complication"
    }
}