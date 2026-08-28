package app.aaps.plugins.sync.nfcCommands

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.aaps.core.interfaces.clientcontrol.ClientControlActionDispatcher
import app.aaps.core.interfaces.pump.BolusProgressData
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import app.aaps.core.ui.compose.pump.PumpActivityDialog
import app.aaps.core.ui.compose.pump.PumpCommunicationStatus
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import javax.inject.Inject

open class NfcControlActivity : FragmentActivity() {

    @Inject lateinit var nfcPlugin: NfcCommandsPlugin
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var bolusProgressData: BolusProgressData
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var clientControlActionDispatcher: ClientControlActionDispatcher

    private val pumpCommunicationStatus by lazy {
        PumpCommunicationStatus(rxBus, commandQueue, this, lifecycleScope)
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pendingTag by mutableStateOf<NfcCreatedTag?>(null)
    private var awaitingBolusFinish by mutableStateOf(false)

    companion object {
        private const val BOLUS_START_GRACE_MS = 500L
        private const val BOLUS_START_POLL_MS = 20L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            CompositionLocalProvider(
                LocalPreferences provides preferences,
                LocalDateUtil provides dateUtil,
                LocalConfig provides config,
                LocalSnackbarHostState provides snackbarHostState
            ) {
                AapsTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Transparent
                        ) {
                            val currentTag = pendingTag
                            if (currentTag != null) {
                                NfcExecutionConfirmationDialog(
                                    tag = currentTag,
                                    plugin = nfcPlugin,
                                    onConfirm = {
                                        pendingTag = null
                                        scope.launch {
                                            nfcPlugin.updateLastScanned(currentTag.tagUid)
                                            nfcPlugin.executeWithFeedback(currentTag.commands, currentTag.name)
                                            withContext(Dispatchers.Main) {
                                                if (hasBolusCommand(currentTag.commands)) {
                                                    var waited = 0L
                                                    while (bolusProgressData.state.value == null && waited < BOLUS_START_GRACE_MS) {
                                                        delay(BOLUS_START_POLL_MS)
                                                        waited += BOLUS_START_POLL_MS
                                                    }
                                                    if (bolusProgressData.state.value != null) {
                                                        awaitingBolusFinish = true
                                                    } else {
                                                        finishWithTransition()
                                                    }
                                                } else {
                                                    finishWithTransition()
                                                }
                                            }
                                        }
                                    },
                                    onDismiss = {
                                        pendingTag = null
                                        finish()
                                    }
                                )
                            }

                            val bolusState by bolusProgressData.state.collectAsStateWithLifecycle()

                            LaunchedEffect(bolusState, awaitingBolusFinish) {
                                if (awaitingBolusFinish && bolusState == null) finishWithTransition()
                            }

                            bolusState?.let { state ->
                                if (!state.isSMB) {
                                    val pumpStatus by pumpCommunicationStatus.statusBannerFlow.collectAsStateWithLifecycle()
                                    val queueStatus by pumpCommunicationStatus.queueStatusFlow.collectAsStateWithLifecycle()
                                    PumpActivityDialog(
                                        bolusState = state,
                                        pumpStatus = pumpStatus?.text ?: "",
                                        queueStatus = queueStatus ?: "",
                                        isModal = true,
                                        onStop = {
                                            if (config.AAPSCLIENT) {
                                                clientControlActionDispatcher.stopBolus()
                                                bolusProgressData.stopPressed()
                                            } else {
                                                commandQueue.cancelAllBoluses(null)
                                            }
                                        },
                                        onDismiss = { bolusProgressData.clear() }
                                    )
                                }
                            }
                        }
                        GlobalSnackbarHost(
                            rxBus = rxBus,
                            hostState = snackbarHostState,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }

        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        scope.launch {
            when (val prep = nfcPlugin.processIntent(intent)) {
                is NfcPrepareResult.Error -> {
                    if (prep.message.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@NfcControlActivity, prep.message, Toast.LENGTH_LONG).show()
                        }
                    }
                    if (pendingTag == null) finish()
                }
                is NfcPrepareResult.Ready -> {
                    withContext(Dispatchers.Main) {
                        pendingTag = NfcCreatedTag(
                            tagUid = prep.tagUid,
                            name = prep.tagName,
                            commands = prep.commands,
                            createdAtMillis = 0L
                        )
                    }
                }
            }
        }
    }

    private fun finishWithTransition() {
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }?.let { startActivity(it) }
        finish()
    }

    private fun hasBolusCommand(commands: List<String>): Boolean =
        commands.any { cmd ->
            val code = runCatching {
                NfcCommandCode.valueOf(JSONObject(cmd).optString(NfcJsonKeys.CODE))
            }.getOrNull()
            code == NfcCommandCode.BOLUS || code == NfcCommandCode.BOLUS_WIZARD
        }
}