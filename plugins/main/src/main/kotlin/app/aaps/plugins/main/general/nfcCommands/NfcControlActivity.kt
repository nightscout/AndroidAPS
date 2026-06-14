package app.aaps.plugins.main.general.nfcCommands

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
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
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import androidx.compose.ui.Alignment
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

open class NfcControlActivity : FragmentActivity() {
    @Inject lateinit var nfcPlugin: NfcCommandsPlugin
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var rxBus: RxBus

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pendingTag by mutableStateOf<NfcCreatedTag?>(null)

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
                                                finishWithTransition()
                                            }
                                        }
                                    },
                                    onDismiss = {
                                        pendingTag = null
                                        finish()
                                    }
                                )
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
                            android.widget.Toast.makeText(this@NfcControlActivity, prep.message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                    if (pendingTag == null) finish()
                }
                is NfcPrepareResult.Ready -> {
                    if (nfcPlugin.autoAcceptEnabled()) {
                        nfcPlugin.updateLastScanned(prep.tagUid)
                        nfcPlugin.executeWithFeedback(prep.commands, prep.tagName)
                        withContext(Dispatchers.Main) {
                            finishWithTransition()
                        }
                    } else {
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
    }

    private fun finishWithTransition() {
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }?.let { startActivity(it) }
        finish()
    }
}
