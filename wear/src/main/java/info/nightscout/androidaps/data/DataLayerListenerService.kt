/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.nightscout.androidaps.data

import android.annotation.TargetApi
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.wear.tiles.TileService
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.*
import dagger.android.AndroidInjection
import info.nightscout.androidaps.R
import info.nightscout.androidaps.events.EventWearToMobileAction
import info.nightscout.androidaps.events.EventWearToMobileChange
import info.nightscout.androidaps.events.EventWearToMobileConfirm
import info.nightscout.androidaps.interaction.AAPSPreferences
import info.nightscout.androidaps.interaction.actions.AcceptActivity
import info.nightscout.androidaps.interaction.actions.CPPActivity
import info.nightscout.androidaps.interaction.utils.Persistence
import info.nightscout.androidaps.interaction.utils.WearUtil
import info.nightscout.androidaps.plugins.bus.RxBus
import info.nightscout.androidaps.tile.ActionsTileService
import info.nightscout.androidaps.tile.QuickWizardTileService
import info.nightscout.androidaps.tile.TempTargetTileService
import info.nightscout.androidaps.utils.rx.AapsSchedulers
import info.nightscout.shared.SafeParse.stringToInt
import info.nightscout.shared.logging.AAPSLogger
import info.nightscout.shared.logging.LTag
import info.nightscout.shared.sharedPreferences.SP
import info.nightscout.shared.weardata.ActionData
import info.nightscout.shared.weardata.WearConstants
import info.nightscout.shared.weardata.WearConstants.Companion.KEY_ACTION_DATA
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DataLayerListenerService : WearableListenerService() {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var wearUtil: WearUtil
    @Inject lateinit var persistence: Persistence
    @Inject lateinit var sp: SP
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var wearConstants: WearConstants

    private val dataClient by lazy { Wearable.getDataClient(this) }
    private val messageClient by lazy { Wearable.getMessageClient(this) }
    private val capabilityClient by lazy { Wearable.getCapabilityClient(this) }
    //private val nodeClient by lazy { Wearable.getNodeClient(this) }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private val disposable = CompositeDisposable()

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
        handler.post { updateTranscriptionCapability() }
        disposable += rxBus
            .toObservable(EventWearToMobileAction::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe { sendMessage(wearConstants.W_M_INITIATE_ACTION, it.actionData.serialize().toByteArray()) }
        disposable += rxBus
            .toObservable(EventWearToMobileConfirm::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                NotificationManagerCompat.from(this).cancel(CONFIRM_NOTIF_ID)
                sendMessage(wearConstants.W_M_CONFIRM_ACTION, it.actionData.serialize().toByteArray())
            }
        disposable += rxBus
            .toObservable(EventWearToMobileChange::class.java)
            .observeOn(aapsSchedulers.io)
            .subscribe {
                NotificationManagerCompat.from(this).cancel(CHANGE_NOTIF_ID)
                sendMessage(wearConstants.W_M_CONFIRM_ACTION, it.actionData.serialize().toByteArray())
            }
    }

    override fun onPeerConnected(p0: Node) {
        super.onPeerConnected(p0)
    }

    override fun onCapabilityChanged(p0: CapabilityInfo) {
        super.onCapabilityChanged(p0)
        handler.post { updateTranscriptionCapability() }
        aapsLogger.debug(LTag.WEAR, "onCapabilityChanged:  ${p0.name} ${p0.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        disposable.clear()
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        //aapsLogger.debug(LTag.WEAR, "onDataChanged")

        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val path = event.dataItem.uri.path

                aapsLogger.debug(LTag.WEAR, "onDataChanged: Path: $path, EventDataItem=${event.dataItem}")
                try {
                    when (path) {
                        wearConstants.M_W_BOLUS_PROGRESS                     -> {
                            val progress = DataMapItem.fromDataItem(event.dataItem).dataMap.getInt("progresspercent", 0)
                            val status = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("progressstatus", "")
                            showBolusProgress(progress, status)
                        }
                        // remove when finished -> converted to message
                        wearConstants.M_W_ACTION_CONFIRMATION_REQUEST        -> {
                            val title = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("title") ?: return@forEach
                            val message = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("message") ?: return@forEach
                            val actionstring = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("actionstring") ?: return@forEach
                            if ("opencpp" == title && actionstring.startsWith("opencpp")) {
                                val act = actionstring.split("\\s+").toTypedArray()
                                startActivity(Intent(this@DataLayerListenerService, CPPActivity::class.java).also { intent ->
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    intent.putExtras(Bundle().also {
                                        it.putInt("percentage", stringToInt(act[1]))
                                        it.putInt("timeshift", stringToInt(act[2]))
                                    })
                                })
                            } else {
                                showConfirmationDialog(title, message, actionstring)
                            }
                        }

                        wearConstants.M_W_STATUS                             -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            val messageIntent = Intent()
                            messageIntent.action = Intent.ACTION_SEND
                            messageIntent.putExtra("status", dataMap.toBundle())
                            persistence.storeDataMap(RawDisplayData.STATUS_PERSISTENCE_KEY, dataMap)
                            LocalBroadcastManager.getInstance(this@DataLayerListenerService).sendBroadcast(messageIntent)
                        }

                        wearConstants.M_W_BASAL                              -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            val messageIntent = Intent()
                            messageIntent.action = Intent.ACTION_SEND
                            messageIntent.putExtra("basals", dataMap.toBundle())
                            persistence.storeDataMap(RawDisplayData.BASALS_PERSISTENCE_KEY, dataMap)
                            LocalBroadcastManager.getInstance(this@DataLayerListenerService).sendBroadcast(messageIntent)
                        }

                        wearConstants.M_W_PREFERENCES                        -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            val keyControl = getString(R.string.key_wear_control)
                            if (dataMap.containsKey(keyControl)) {
                                val previousWearControl = sp.getBoolean(keyControl, false)
                                val wearControl: Boolean = dataMap.getBoolean(keyControl, false)
                                sp.putBoolean(keyControl, wearControl)
                                if (wearControl != previousWearControl) {
                                    updateTiles()
                                }
                            }
                            val keyPercentage = getString(R.string.key_boluswizard_percentage)
                            if (dataMap.containsKey(keyPercentage)) {
                                val wpercentage: Int = dataMap.getInt(keyPercentage, 100)
                                sp.putInt(keyPercentage, wpercentage)
                            }
                            val keyUnits = getString(R.string.key_units_mgdl)
                            if (dataMap.containsKey(keyUnits)) {
                                val mgdl: Boolean = dataMap.getBoolean(keyUnits, true)
                                sp.putBoolean(keyUnits, mgdl)
                            }
                            val keyMaxCarbs = getString(R.string.key_treatmentssafety_maxcarbs)
                            if (dataMap.containsKey(keyMaxCarbs)) {
                                val maxCarbs: Int = dataMap.getInt(keyMaxCarbs, 48)
                                sp.putInt(keyMaxCarbs, maxCarbs)
                            }
                            val keyMaxBolus = getString(R.string.key_treatmentssafety_maxbolus)
                            if (dataMap.containsKey(keyMaxBolus)) {
                                sp.putDouble(keyMaxBolus, dataMap.getDouble(keyMaxBolus, 3.0))
                            }
                        }

                        wearConstants.M_W_QUICK_WIZARD                       -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            aapsLogger.info(LTag.WEAR, "onDataChanged: QUICK_WIZARD_PATH$dataMap")
                            dataMap.remove("timestamp")
                            val key = getString(R.string.key_quick_wizard_data_map)
                            val dataString = Base64.encodeToString(dataMap.toByteArray(), Base64.DEFAULT)
                            if (dataString != sp.getString(key, "")) {
                                sp.putString(key, dataString)
                                // Todo maybe add debounce function, due to 20 seconds update limit?
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    TileService.getUpdater(this@DataLayerListenerService).requestUpdate(QuickWizardTileService::class.java)
                                }
                                aapsLogger.info(LTag.WEAR, "onDataChanged: updated QUICK_WIZARD")
                            } else {
                                aapsLogger.info(LTag.WEAR, "onDataChanged: ignore update")
                            }
                        }

                        wearConstants.M_W_ACTION_CHANGE_CONFIRMATION_REQUEST -> {
                            val title = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("title") ?: return@forEach
                            val message = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("message") ?: return@forEach
                            val actionstring = DataMapItem.fromDataItem(event.dataItem).dataMap.getString("actionstring") ?: return@forEach
                            notifyChangeRequest(title, message, actionstring)
                        }

                        wearConstants.M_W_ACTION_CANCEL_NOTIFICATION_REQUEST -> {
                            //val actionstring = DataMapItem.fromDataItem(event.getDataItem()).dataMap.getString("actionstring") ?: return@forEach
                            cancelNotificationRequest()
                        }

                        wearConstants.M_W_DATA                               -> {
                            val dataMap = DataMapItem.fromDataItem(event.dataItem).dataMap
                            val messageIntent = Intent()
                            messageIntent.action = Intent.ACTION_SEND
                            messageIntent.putExtra("data", dataMap.toBundle())
                            persistence.storeDataMap(RawDisplayData.DATA_PERSISTENCE_KEY, dataMap)
                            LocalBroadcastManager.getInstance(this@DataLayerListenerService).sendBroadcast(messageIntent)
                        }
                    }
                } catch (exception: Exception) {
                    aapsLogger.error(LTag.WEAR, "onDataChanged failed", exception)
                }
            }
        }
        super.onDataChanged(dataEvents)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)
        aapsLogger.debug(LTag.WEAR, "onMessageReceived: $messageEvent")

        when (messageEvent.path) {
            wearConstants.M_W_PING                        -> sendMessage(wearConstants.W_M_PONG, byteArrayOf())
            wearConstants.M_W_OPEN_SETTINGS               -> startActivity(Intent(this@DataLayerListenerService, AAPSPreferences::class.java).also { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })

            wearConstants.M_W_ACTION_CONFIRMATION_REQUEST -> {
                val command = ActionData.deserialize(String(messageEvent.data)) as ActionData.ConfirmAction
                if (command.originalCommand is ActionData.OpenProfileSwitch) {
                    val originalCommand = command.originalCommand as ActionData.OpenProfileSwitch
                    startActivity(Intent(this, CPPActivity::class.java).also { intent ->
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtras(Bundle().also {
                            it.putInt("percentage", originalCommand.percentage)
                            it.putInt("timeshift", originalCommand.timeShift)
                        })
                    })
                } else {
                    startActivity(
                        Intent(this, AcceptActivity::class.java).also { intent ->
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            intent.putExtras(
                                Bundle().also { bundle ->
                                    bundle.putString("title", command.title)
                                    bundle.putString("message", command.message)
                                    bundle.putString(KEY_ACTION_DATA, command.originalCommand.serialize())
                                }
                            )
                        })
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESEND          -> sendMessage(wearConstants.W_M_RESEND_DATA, byteArrayOf())

            ACTION_CANCEL_BOLUS    -> {
                //dismiss notification
                val notificationManager = NotificationManagerCompat.from(this)
                notificationManager.cancel(BOLUS_PROGRESS_NOTIF_ID)
                //send cancel-request to phone.
                sendMessage(wearConstants.W_M_CANCEL_BOLUS, byteArrayOf())
            }

            ACTION_CONFIRMATION    -> {
                //dismiss notification
                val notificationManager = NotificationManagerCompat.from(this)
                notificationManager.cancel(CONFIRM_NOTIF_ID)
                intent.getStringExtra("actionstring")?.let { actionString ->
                    sendMessage(wearConstants.W_M_CONFIRM_ACTION, actionString.toByteArray())
                }
            }

            ACTION_CONFIRM_CHANGE  -> {
                //dismiss notification
                val notificationManager = NotificationManagerCompat.from(this)
                notificationManager.cancel(CHANGE_NOTIF_ID)
                intent.getStringExtra("actionstring")?.let { actionString ->
                    sendMessage(wearConstants.W_M_CONFIRM_ACTION, actionString.toByteArray())
                }
            }

            ACTION_INITIATE_ACTION ->
                if (intent.hasExtra("actionstring"))
                    intent.getStringExtra("actionstring")?.let { actionString ->
                        sendMessage(wearConstants.W_M_INITIATE_ACTION, actionString.toByteArray())
                    }
                else if (intent.hasExtra(KEY_ACTION_DATA))
                    intent.getStringExtra(KEY_ACTION_DATA)?.let { actionData ->
                        sendMessage(wearConstants.W_M_INITIATE_ACTION, actionData.toByteArray())
                    }
        }
        return START_STICKY
    }

    private var transcriptionNodeId: String? = null

    private fun updateTranscriptionCapability() {
        val capabilityInfo: CapabilityInfo = Tasks.await(
            capabilityClient.getCapability(PHONE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        )
        aapsLogger.debug(LTag.WEAR, "Nodes: ${capabilityInfo.nodes.joinToString(", ") { it.displayName + "(" + it.id + ")" }}")
        transcriptionNodeId = pickBestNodeId(capabilityInfo.nodes)
        aapsLogger.debug(LTag.WEAR, "Selected node: $transcriptionNodeId")
    }

    // Find a nearby node or pick one arbitrarily
    private fun pickBestNodeId(nodes: Set<Node>): String? =
        nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id

    private fun sendData(path: String, vararg params: DataMap) {
        scope.launch {
            try {
                for (dm in params) {
                    val request = PutDataMapRequest.create(path).apply {
                        dataMap.putAll(dm)
                    }
                        .asPutDataRequest()
                        .setUrgent()

                    val result = dataClient.putDataItem(request).await()
                    aapsLogger.debug(LTag.WEAR, "sendData: ${result.uri} ${params.joinToString()}")
                }
            } catch (cancellationException: CancellationException) {
                throw cancellationException
            } catch (exception: Exception) {
                aapsLogger.error(LTag.WEAR, "DataItem failed: $exception")
            }
        }
    }

    private fun sendMessage(path: String, data: ByteArray) {
        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path")
        transcriptionNodeId?.also { nodeId ->
            messageClient
                .sendMessage(nodeId, path, data).apply {
                    addOnSuccessListener { }
                    addOnFailureListener {
                        aapsLogger.debug(LTag.WEAR, "sendMessage:  $path failure")
                    }
                }
        }
    }

    private fun updateTiles() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TileService.getUpdater(this)
                .requestUpdate(ActionsTileService::class.java)
            TileService.getUpdater(this)
                .requestUpdate(TempTargetTileService::class.java)
            TileService.getUpdater(this)
                .requestUpdate(QuickWizardTileService::class.java)
        }
    }

    private fun notifyChangeRequest(title: String, message: String, actionstring: String) {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "AAPS Open Loop"
            val description = "Open Loop request notification"
            val channel = NotificationChannel(AAPS_NOTIFY_CHANNEL_ID_OPENLOOP, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            channel.enableVibration(true)

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        var builder = NotificationCompat.Builder(this, AAPS_NOTIFY_CHANNEL_ID_OPENLOOP)
        builder = builder.setSmallIcon(R.drawable.notif_icon)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVibrate(longArrayOf(1000, 1000, 1000, 1000, 1000))

        // Creates an explicit intent for an Activity in your app
        val intent = Intent(this, AcceptActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val params = Bundle()
        params.putString("title", title)
        params.putString("message", message)
        params.putString("actionstring", actionstring)
        intent.putExtras(params)
        val resultPendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        builder = builder.setContentIntent(resultPendingIntent)
        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        // mId allows you to update the notification later on.
        mNotificationManager.notify(CHANGE_NOTIF_ID, builder.build())
    }

    private fun cancelNotificationRequest() {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).cancel(CHANGE_NOTIF_ID)
    }

    private fun showBolusProgress(progressPercent: Int, progresStatus: String) {
        val vibratePattern: LongArray
        val vibrate = sp.getBoolean("vibrateOnBolus", true)
        vibratePattern = if (vibrate) longArrayOf(0, 50, 1000) else longArrayOf(0, 1, 1000)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createBolusProgressChannels()
        }
        val cancelIntent = Intent(this, DataLayerListenerService::class.java)
        cancelIntent.action = ACTION_CANCEL_BOLUS
        val cancelPendingIntent = PendingIntent.getService(this, 0, cancelIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notificationBuilder: NotificationCompat.Builder =
            NotificationCompat.Builder(this, if (vibrate) AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS else AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS_SILENT)
                .setSmallIcon(R.drawable.ic_icon)
                .setContentTitle(getString(R.string.bolus_progress))
                .setContentText("$progressPercent% - $progresStatus")
                .setSubText(getString(R.string.press_to_cancel))
                .setContentIntent(cancelPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVibrate(vibratePattern)
                .addAction(R.drawable.ic_cancel, getString(R.string.cancel_bolus), cancelPendingIntent)
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.notify(BOLUS_PROGRESS_NOTIF_ID, notificationBuilder.build())
        notificationManager.cancel(CONFIRM_NOTIF_ID) // multiple watch setup
        if (progressPercent == 100) {
            scheduleDismissBolusProgress(5)
        }
    }

    @TargetApi(value = 26) private fun createBolusProgressChannels() {
        createNotificationChannel(
            longArrayOf(0, 50, 1000),
            AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS,
            getString(R.string.bolus_progress_channel_name),
            getString(R.string.bolus_progress_channel_description)
        )
        createNotificationChannel(
            longArrayOf(0, 1, 1000),
            AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS_SILENT,
            getString(R.string.bolus_progress_silent_channel_name),
            getString(R.string.bolus_progress_silent_channel_description)
        )
    }

    @TargetApi(value = 26) private fun createNotificationChannel(vibratePattern: LongArray, channelID: String, name: CharSequence, description: String) {
        val channel = NotificationChannel(channelID, name, NotificationManager.IMPORTANCE_HIGH)
        channel.description = description
        channel.enableVibration(true)
        channel.vibrationPattern = vibratePattern

        // Register the channel with the system; you can't change the importance
        // or other notification behaviors after this
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun showConfirmationDialog(title: String, message: String, actionstring: String) {
        val intent = Intent(this, AcceptActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val params = Bundle()
        params.putString("title", title)
        params.putString("message", message)
        params.putString("actionstring", actionstring)
        intent.putExtras(params)
        startActivity(intent)
    }

    @Suppress("SameParameterValue")
    private fun scheduleDismissBolusProgress(seconds: Int) {
        Thread {
            SystemClock.sleep(seconds * 1000L)
            NotificationManagerCompat.from(this@DataLayerListenerService)
                .cancel(BOLUS_PROGRESS_NOTIF_ID)
        }.start()
    }

    companion object {

        const val PHONE_CAPABILITY = "androidaps_mobile"

        const val ACTION_RESEND = "com.dexdrip.stephenblack.nightwatch.RESEND_DATA"
        const val ACTION_CANCEL_BOLUS = "com.dexdrip.stephenblack.nightwatch.CANCELBOLUS"
        const val ACTION_CONFIRMATION = "com.dexdrip.stephenblack.nightwatch.CONFIRMACTION"
        const val ACTION_CONFIRM_CHANGE = "com.dexdrip.stephenblack.nightwatch.CONFIRMCHANGE"
        val ACTION_INITIATE_ACTION = DataLayerListenerService::class.java.name + ".INITIATE_ACTION"

        const val BOLUS_PROGRESS_NOTIF_ID = 1
        const val CONFIRM_NOTIF_ID = 2
        const val CHANGE_NOTIF_ID = 556677

        const val AAPS_NOTIFY_CHANNEL_ID_OPENLOOP = "AndroidAPS-OpenLoop"
        const val AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS = "bolus progress vibration"
        const val AAPS_NOTIFY_CHANNEL_ID_BOLUSPROGRESS_SILENT = "bolus progress silent"

        fun initiateAction(context: Context, actionstring: String) {
            context.startService(
                Intent(context, DataLayerListenerService::class.java).also {
                    it.putExtra("actionstring", actionstring)
                    it.action = ACTION_INITIATE_ACTION
                })
        }

        fun requestData(context: Context) {
            context.startService(
                Intent(context, DataLayerListenerService::class.java).also { it.action = ACTION_RESEND })
        }

        fun confirmAction(context: Context, actionstring: String) {
            context.startService(
                Intent(context, DataLayerListenerService::class.java).also {
                    it.putExtra("actionstring", actionstring)
                    if (actionstring == "changeRequest") it.action = ACTION_CONFIRM_CHANGE
                    else it.action = ACTION_CONFIRMATION
                })
        }
    }
}
