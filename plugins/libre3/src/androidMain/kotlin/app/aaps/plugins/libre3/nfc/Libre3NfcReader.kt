package app.aaps.plugins.libre3.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import app.aaps.plugins.libre3.Libre3Log
import app.aaps.plugins.libre3.Libre3LogMarkers
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Android side of the NFC step: turns reader mode on while a screen is in front of the user, and
 * runs one [Libre3NfcSession] on the tag that is found.
 *
 * ⚠️ ASYNC IMPACT: Android calls back on a binder thread. The tag work is moved to one dedicated
 * thread, so it never runs on the main thread and never on the Bluetooth executor. A tag link must
 * not be shared between threads.
 *
 * The reader is on only while the screen is resumed, and it is turned off after the first
 * successful scan so a second A8 cannot replace the PIN. [disable] is also called when the
 * screen pauses, so a phone in a pocket does not keep scanning.
 */
class Libre3NfcReader(private val session: Libre3NfcSession) {

    private val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "libre3-nfc").apply { isDaemon = true }
    }

    @Volatile
    private var adapter: NfcAdapter? = null

    /**
     * True after one scan has stored a sensor. Extra tags are ignored until [enable] is called
     * again. A second A8 about a second later would replace the PIN the first scan just wrote,
     * and Bluetooth would then pair with a PIN the sensor no longer has.
     */
    @Volatile
    private var scanFinished = false

    /** True while one tag is being read, so a second callback cannot start another A8. */
    @Volatile
    private var scanBusy = false

    /**
     * Turns reader mode on for [activity].
     *
     * @param onResult called on the main thread when a sensor was read and stored.
     * @param onError called on the main thread when the scan failed, with the reason the screen
     *   should translate. Details for support stay in the log.
     * @return false when this phone has no NFC or NFC is switched off, so the screen can say so.
     */
    fun enable(
        activity: Activity,
        onResult: (Libre3NfcScanResult) -> Unit,
        onError: (Libre3NfcFailure) -> Unit,
    ): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: return false
        if (!nfcAdapter.isEnabled) return false
        scanFinished = false
        scanBusy = false
        adapter = nfcAdapter
        nfcAdapter.enableReaderMode(
            activity,
            { tag -> executor.execute { handleTag(activity, tag, onResult, onError) } },
            NfcAdapter.FLAG_READER_NFC_V or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
        Libre3Log.i("${Libre3LogMarkers.NFC}: reader on")
        return true
    }

    /** Turns reader mode off again. Safe to call more than once. */
    fun disable(activity: Activity) {
        adapter?.let {
            try {
                it.disableReaderMode(activity)
            } catch (e: Exception) {
                Libre3Log.w("${Libre3LogMarkers.NFC}: reader could not be turned off, ${e.javaClass.simpleName}")
            }
        }
        adapter = null
        Libre3Log.i("${Libre3LogMarkers.NFC}: reader off")
    }

    /**
     * Releases the NFC thread. The screen that created this reader must call it when it is
     * destroyed, otherwise every new screen leaves one more thread behind.
     */
    fun shutdown() {
        executor.shutdown()
    }

    private fun handleTag(
        activity: Activity,
        tag: Tag,
        onResult: (Libre3NfcScanResult) -> Unit,
        onError: (Libre3NfcFailure) -> Unit,
    ) {
        if (scanFinished || scanBusy) {
            Libre3Log.i("${Libre3LogMarkers.NFC}: extra tag ignored")
            return
        }
        scanBusy = true
        val nfcV = NfcV.get(tag)
        if (nfcV == null) {
            scanBusy = false
            deliverError(activity, onError, Libre3NfcFailure.NOT_A_LIBRE3_SENSOR)
            return
        }
        try {
            nfcV.connect()
            val result = session.scan { frame -> Libre3NfcCommands.checkAnswerFlag(nfcV.transceive(frame)) }
            scanFinished = true
            // The reader is turned off on the main thread before the screen is told, so a second
            // A8 cannot start while the first result is still being stored.
            deliverOnMain(activity) {
                disable(activity)
                onResult(result)
            }
        } catch (e: Libre3NfcException) {
            // The message is for the log and for support, never for the screen.
            Libre3Log.e("${Libre3LogMarkers.ERROR}: NFC scan failed, ${e.message}")
            scanBusy = false
            deliverError(activity, onError, e.failure)
        } catch (e: Exception) {
            Libre3Log.e("${Libre3LogMarkers.ERROR}: NFC link failed, ${e.javaClass.simpleName}")
            scanBusy = false
            deliverError(activity, onError, Libre3NfcFailure.LINK_LOST)
        } finally {
            try {
                nfcV.close()
            } catch (e: Exception) {
                Libre3Log.w("${Libre3LogMarkers.NFC}: tag could not be closed, ${e.javaClass.simpleName}")
            }
        }
    }

    private fun deliverError(activity: Activity, onError: (Libre3NfcFailure) -> Unit, failure: Libre3NfcFailure) {
        deliverOnMain(activity) { onError(failure) }
    }

    private fun deliverOnMain(activity: Activity, action: () -> Unit) {
        if (activity.isDestroyed) return
        activity.runOnUiThread {
            if (!activity.isDestroyed) action()
        }
    }
}
