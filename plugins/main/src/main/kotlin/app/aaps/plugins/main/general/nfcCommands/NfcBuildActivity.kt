package app.aaps.plugins.main.general.nfcCommands

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.plugins.main.R
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * Standalone Activity that owns NFC write-mode and reader-mode lifecycle.
 * Hosts [NfcBuildFragment] and is launched when the user taps Add in My Tags.
 */
open class NfcBuildActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    @Inject lateinit var aapsLogger: AAPSLogger

    var nfcAdapter: NfcAdapter? = null

    @Volatile private var isWritingMode = false
    private var pendingCommands: List<String> = emptyList()
    private var pendingTagName: String = ""

    // The physical tag currently being written; stored so issueToken can read its UID.
    private var pendingTag: Tag? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var readerModeDisableRunnable: Runnable? = null

    companion object {
        private const val POST_WRITE_READER_MODE_HOLD_MS = 3_000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.nfccommands_build_activity)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
    }

    // ── Write mode lifecycle ──────────────────────────────────────────────────

    /**
     * Enters write mode: shows the write dialog and enables NFC reader mode.
     * Called from [NfcBuildFragment] when the user taps Write.
     */
    fun startWriteMode(
        commands: List<String>,
        tagName: String = "",
    ) {
        if (commands.isEmpty()) return
        pendingCommands = commands
        pendingTagName = tagName
        isWritingMode = true
        showWriteDialog(commands)
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B,
            null,
        )
    }

    // Called on the NFC binder thread by the Android NFC stack.
    override fun onTagDiscovered(tag: Tag) {
        if (!isWritingMode) return
        pendingTag = tag
        val commands = pendingCommands
        val name = pendingTagName.ifBlank { commands.firstOrNull() ?: return }
        val issued = issueToken(commands)
        val success = buildAndWriteNdef(tag, issued.token)
        val message = buildWriteMessage(success)
        persistWriteAttempt(name, success, message)
        if (success) {
            persistWrittenTag(issued, name, commands)
        }
        disableWritingMode(delayReaderModeDisable = success)
    }

    /**
     * Exits write mode. When [delayReaderModeDisable] is true the NFC reader mode stays
     * active for [POST_WRITE_READER_MODE_HOLD_MS] to suppress immediate re-execution of
     * the freshly-written tag while it is still in field.
     */
    fun disableWritingMode(delayReaderModeDisable: Boolean = false) {
        isWritingMode = false
        if (delayReaderModeDisable) {
            scheduleReaderModeDisable()
        } else {
            nfcAdapter?.disableReaderMode(this)
        }
    }

    override fun onStop() {
        super.onStop()
        readerModeDisableRunnable?.let { mainHandler.removeCallbacks(it) }
        nfcAdapter?.disableReaderMode(this)
    }

    // ── Open methods — overridable in subclasses and stubbable in unit tests ──

    /** Issues a new signed token. UID of [pendingTag] is included as the `tid` claim. */
    open fun issueToken(commands: List<String>): NfcIssuedToken =
        NfcTokenSupport.issueToken(this, commands, tagUid = NfcTokenSupport.tagUidHex(pendingTag?.id))

    /** Writes [token] as an NDEF MIME record onto [tag]. Returns true on success. */
    open fun buildAndWriteNdef(
        tag: Tag,
        token: String,
    ): Boolean {
        val record = NdefRecord.createMime(NfcTokenSupport.MIME_TYPE, token.toByteArray())
        val message = NdefMessage(arrayOf(record))
        val ndefFormatable = NdefFormatable.get(tag)
        val ndef = Ndef.get(tag)
        return when {
            ndefFormatable != null -> writeNdefFormatable(ndefFormatable, message)
            ndef != null -> writeNdef(ndef, message)
            else -> {
                aapsLogger.error(LTag.NFC, "Tag supports neither Ndef nor NdefFormatable. Techs: ${tag.techList.joinToString()}")
                false
            }
        }
    }

    /** Returns the user-facing result message for a write attempt. */
    open fun buildWriteMessage(success: Boolean): String =
        if (success) getString(R.string.nfccommands_tag_written) else getString(R.string.nfccommands_tag_write_error)

    /** Appends a WRITE log entry for the attempt. */
    open fun persistWriteAttempt(
        name: String,
        success: Boolean,
        message: String,
    ) {
        NfcTokenSupport.appendLogEntry(
            this,
            NfcLogEntry(
                timestamp = System.currentTimeMillis(),
                tagName = name,
                action = "WRITE",
                success = success,
                message = message,
            ),
        )
    }

    /** Persists the successfully written tag to SharedPreferences. */
    open fun persistWrittenTag(
        token: NfcIssuedToken,
        name: String,
        commands: List<String>,
    ) {
        NfcTokenSupport.saveCreatedTag(
            this,
            NfcCreatedTag(
                id = token.tokenId,
                name = name,
                commands = commands,
                token = token.token,
                createdAtMillis = token.issuedAtMillis,
                expiresAtMillis = token.expiresAtMillis,
            ),
        )
    }

    /** Shows a dialog prompting the user to hold their tag to the phone. */
    open fun showWriteDialog(commands: List<String>) {
        // Implemented in the concrete Activity; overridden/stubbed in tests.
    }

    /** Schedules NFC reader mode to be disabled after [POST_WRITE_READER_MODE_HOLD_MS]. */
    open fun scheduleReaderModeDisable() {
        val adapter = nfcAdapter ?: return
        readerModeDisableRunnable?.let { mainHandler.removeCallbacks(it) }
        val runnable = Runnable { adapter.disableReaderMode(this) }
        readerModeDisableRunnable = runnable
        mainHandler.postDelayed(runnable, POST_WRITE_READER_MODE_HOLD_MS)
    }

    // ── Private NFC I/O helpers ───────────────────────────────────────────────

    private fun writeNdef(
        ndef: Ndef,
        message: NdefMessage,
    ): Boolean =
        try {
            ndef.connect()
            try {
                ndef.writeNdefMessage(message)
                true
            } finally {
                ndef.close()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NFC, "Failed to write NDEF tag", e)
            false
        }

    private fun writeNdefFormatable(
        formatable: NdefFormatable,
        message: NdefMessage,
    ): Boolean =
        try {
            formatable.connect()
            try {
                formatable.format(message)
                true
            } finally {
                formatable.close()
            }
        } catch (e: Exception) {
            aapsLogger.error(LTag.NFC, "Failed to format and write NDEF tag", e)
            false
        }
}
