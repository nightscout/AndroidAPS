package app.aaps.plugins.main.general.nfcCommands

import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.LocalConfig
import app.aaps.core.ui.compose.LocalDateUtil
import app.aaps.core.ui.compose.LocalPreferences
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.dialogs.GlobalSnackbarHost
import app.aaps.core.ui.compose.icons.IcPluginNfc
import app.aaps.plugins.main.R
import dagger.android.AndroidInjection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import app.aaps.core.ui.R as CoreUiR

open class NfcControlActivity : FragmentActivity() {
    @Inject lateinit var nfcPlugin: NfcCommandsPlugin
    @Inject lateinit var nfcTagStore: NfcTagStore
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var rxBus: app.aaps.core.interfaces.rx.bus.RxBus

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

        scope.launch {
            handleIntent(intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        scope.launch {
            handleIntent(intent)
        }
    }

    @Composable
    private fun NfcExecutionConfirmationDialog(
        tag: NfcCreatedTag,
        onConfirm: () -> Unit,
        onDismiss: () -> Unit
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = {
                Icon(
                    imageVector = IcPluginNfc,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    text = tag.name,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    tag.commands.forEach { cmdJson ->
                        NfcCommandDisplay(commandJson = cmdJson, plugin = nfcPlugin)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(CoreUiR.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
        )
    }

    private fun finishWithTransition() {
        packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }?.let { startActivity(it) }
        finish()
    }

    suspend fun handleIntent(intent: Intent?) {
        if (!nfcPlugin.isEnabled()) {
            aapsLogger.debug(LTag.NFC, "NFC Plugin is disabled. Ignoring tag.")
            showToast(rh.gs(R.string.nfccommands_plugin_disabled))
            finish()
            return
        }

        if (intent == null) {
            finish()
            return
        }

        @Suppress("DEPRECATION")
        val nfcTag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (nfcTag == null) {
            aapsLogger.debug(LTag.NFC, "Rejected intent without physical NFC tag")
            finish()
            return
        }

        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED -> handleNdefIntent(intent, nfcTag)
            NfcAdapter.ACTION_TECH_DISCOVERED -> handleTagIntent(nfcTag)
            NfcAdapter.ACTION_TAG_DISCOVERED  -> handleTagIntent(nfcTag)
            else -> finish()
        }
    }

    private suspend fun handleNdefIntent(intent: Intent, nfcTag: Tag) {
        @Suppress("DEPRECATION")
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
        if (rawMsgs.isNullOrEmpty()) {
            finish()
            return
        }
        val message = rawMsgs[0] as? NdefMessage ?: run { finish(); return }
        val record = message.records?.firstOrNull() ?: run { finish(); return }

        if (record.tnf != NdefRecord.TNF_MIME_MEDIA ||
            String(record.type, StandardCharsets.US_ASCII) != NfcTagStore.MIME_TYPE
        ) {
            aapsLogger.debug(LTag.NFC, "Rejected NFC record with unexpected TNF/type")
            finish()
            return
        }

        val tagUid = NfcTagStore.tagUidHex(nfcTag.id) ?: run { finish(); return }
        prepareExecutionByUid(tagUid, showErrorToast = true)
    }

    private suspend fun handleTagIntent(nfcTag: Tag) {
        val tagUid = NfcTagStore.tagUidHex(nfcTag.id) ?: run { finish(); return }
        aapsLogger.debug(LTag.NFC, "TAG_DISCOVERED fallback, UID: $tagUid")
        prepareExecutionByUid(tagUid, showErrorToast = false)
    }

    private suspend fun prepareExecutionByUid(tagUid: String, showErrorToast: Boolean) {
        aapsLogger.debug(LTag.NFC, "NFC tag scanned, UID: $tagUid")
        if (nfcTagStore.isJustWritten(tagUid)) {
            finish()
            return
        }
        
        when (val prep = nfcPlugin.prepareExecution(tagUid)) {
            is NfcPrepareResult.Error -> {
                if (showErrorToast) showToast(prep.message)
                finish()
            }
            is NfcPrepareResult.Ready -> {
                withContext(Dispatchers.Main) {
                    pendingTag = NfcCreatedTag(
                        tagUid = prep.tagUid,
                        name = prep.tagName,
                        commands = prep.commands,
                        createdAtMillis = 0L // Not strictly needed for the dialog
                    )
                }
            }
        }
    }

    private fun showToast(message: String) {
        runCatching {
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
