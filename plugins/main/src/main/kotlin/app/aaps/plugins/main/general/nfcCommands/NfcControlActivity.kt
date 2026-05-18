package app.aaps.plugins.main.general.nfcCommands

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.plugins.main.R
import dagger.android.AndroidInjection
import java.nio.charset.StandardCharsets
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import javax.inject.Inject

open class NfcControlActivity : Activity() {
    @Inject lateinit var nfcPlugin: NfcCommandsPlugin

    @Inject lateinit var aapsLogger: AAPSLogger

    @Inject lateinit var rh: ResourceHelper

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private var currentTask: Future<*>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        currentTask = executor.submit {
            handleIntent(intent)
            runOnUiThread {
                packageManager.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }?.let { startActivity(it) }
                finish()
            }
        }
    }

    override fun onDestroy() {
        currentTask?.cancel(true)
        executor.shutdown()
        super.onDestroy()
    }

    fun handleIntent(intent: Intent?) {
        if (!nfcPlugin.isEnabled()) {
            aapsLogger.debug(LTag.NFC, "NFC Plugin is disabled. Ignoring tag.")
            showToast(rh.gs(R.string.nfccommands_plugin_disabled))
            return
        }

        if (intent == null || NfcAdapter.ACTION_NDEF_DISCOVERED != intent.action) return

        // Require a physical Tag object. Only the Android NFC subsystem can supply this;
        // an explicit intent crafted by another app cannot forge a real Tag instance,
        // so this check enforces that an actual NFC scan took place.
        // getParcelableExtra(String) deprecated in API 33; type-safe overload requires API 33+, minSdk=26
        @Suppress("DEPRECATION")
        val nfcTag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
        if (nfcTag == null) {
            aapsLogger.debug(LTag.NFC, "Rejected intent without physical NFC tag")
            return
        }

        // getParcelableArrayExtra(String) deprecated in API 33; type-safe overload requires API 33+, minSdk=26
        @Suppress("DEPRECATION")
        val rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES) ?: return
        if (rawMsgs.isEmpty()) return
        val message = rawMsgs[0] as? NdefMessage ?: return
        val record = message.records?.firstOrNull() ?: return

        // Validate the record type in-app. The manifest <intent-filter> enforces the MIME type
        // for implicit NFC dispatch, but not for explicit intents to this exported activity.
        if (record.tnf != NdefRecord.TNF_MIME_MEDIA ||
            String(record.type, StandardCharsets.US_ASCII) != NfcTokenSupport.MIME_TYPE
        ) {
            aapsLogger.debug(LTag.NFC, "Rejected NFC record with unexpected TNF/type")
            return
        }

        val tagUid = NfcTokenSupport.tagUidHex(nfcTag.id) ?: return
        aapsLogger.debug(LTag.NFC, "NFC tag scanned, UID: $tagUid")

        when (val prep = nfcPlugin.prepareExecution(tagUid)) {
            is NfcPrepareResult.Error -> {
                showToast(prep.message)
                return
            }
            is NfcPrepareResult.Ready -> {
                val result = nfcPlugin.executeCascade(prep.commands)
                appendReadLogEntry(tagUid, result)
                showToast(result.message)
            }
        }
    }

    /** Overridable in tests to avoid SharedPreferences access. */
    open fun appendReadLogEntry(tagUid: String, result: NfcExecutionResult) {
        val tagName = NfcTokenSupport.findTagByUid(this, tagUid)?.name ?: tagUid
        NfcTokenSupport.appendLogEntry(
            this,
            NfcLogEntry(
                timestamp = System.currentTimeMillis(),
                tagName = tagName,
                action = "READ",
                success = result.success,
                message = result.message,
            ),
        )
    }

    private fun showToast(message: String) {
        runCatching {
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG)?.show()
            }
        }
    }
}
