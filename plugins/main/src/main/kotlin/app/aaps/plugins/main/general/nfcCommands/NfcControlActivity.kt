package app.aaps.plugins.main.general.nfcCommands

import android.app.Activity
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
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
            runOnUiThread { finish() }
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

        val payload = record.payload ?: return
        val token = String(payload, StandardCharsets.UTF_8)
        aapsLogger.debug(LTag.NFC, "NFC token scanned")

        val tagUid = NfcTokenSupport.tagUidHex(nfcTag.id)
        when (val prep = nfcPlugin.prepareExecution(token, tagUid)) {
            is NfcPrepareResult.Error -> {
                if (prep.eraseTag) erasePhysicalTag(intent)
                showToast(prep.message)
                return
            }
            is NfcPrepareResult.Ready -> {
                if (prep.rewriteWith != null) {
                    val newCreatedTag =
                        NfcCreatedTag(
                            id = prep.rewriteWith.tokenId,
                            name = prep.oldTag!!.name,
                            commands = prep.oldTag.commands,
                            token = prep.rewriteWith.token,
                            createdAtMillis = prep.rewriteWith.issuedAtMillis,
                            expiresAtMillis = prep.rewriteWith.expiresAtMillis,
                        )
                    val ndefMessage =
                        NdefMessage(
                            arrayOf(
                                NdefRecord.createMime(
                                    NfcTokenSupport.MIME_TYPE,
                                    prep.rewriteWith.token.toByteArray(),
                                ),
                            ),
                        )
                    val writeSuccess = writeNdefToPhysicalTag(nfcTag, ndefMessage)

                    if (!writeSuccess) {
                        showToast(rh.gs(R.string.nfccommands_tag_rewrite_failed))
                        return
                    }
                    nfcPlugin.replaceTag(prep.oldTag.id, newCreatedTag)
                }
                val result = nfcPlugin.executeCascade(prep.commands)
                appendReadLogEntry(prep.tokenId, result)
                showToast(result.message)
            }
        }
    }

    /** Overridable in tests to avoid SharedPreferences access. */
    open fun appendReadLogEntry(tokenId: String, result: NfcExecutionResult) {
        val tagName = NfcTokenSupport.loadCreatedTags(this).find { it.id == tokenId }?.name ?: tokenId
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

    /** Overridable in tests to avoid real NFC I/O. */
    open fun writeNdefToPhysicalTag(
        nfcTag: Tag,
        message: NdefMessage,
    ): Boolean =
        try {
            val ndef = Ndef.get(nfcTag) ?: return false
            ndef.connect()
            try {
                ndef.writeNdefMessage(message)
                true
            } finally {
                ndef.close()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NFC, "Failed to rewrite expiring tag", e)
            false
        }

    /** Overridable in tests to avoid real NFC I/O. */
    open fun erasePhysicalTag(intent: Intent) {
        // getParcelableExtra(String) deprecated in API 33; type-safe overload requires API 33+, minSdk=26
        @Suppress("DEPRECATION")
        val nfcTag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        try {
            val ndef = Ndef.get(nfcTag) ?: return
            ndef.connect()
            try {
                ndef.writeNdefMessage(
                    NdefMessage(arrayOf(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null))),
                )
            } finally {
                ndef.close()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NFC, "Failed to erase blacklisted tag", e)
        }
    }

    private fun showToast(message: String) {
        runCatching {
            runOnUiThread {
                Toast.makeText(this, message, Toast.LENGTH_LONG)?.show()
            }
        }
    }
}
