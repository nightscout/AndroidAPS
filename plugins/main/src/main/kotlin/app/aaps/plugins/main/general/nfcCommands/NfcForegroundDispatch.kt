package app.aaps.plugins.main.general.nfcCommands

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.os.Build
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventShowDialog
import app.aaps.core.keys.BooleanKey
import app.aaps.plugins.main.R
import app.aaps.core.keys.interfaces.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Manages NFC foreground dispatch for [activity]. While the activity is resumed and
 * [BooleanKey.NfcForegroundPriority] is enabled, all NFC tags are routed to AAPS
 * ahead of other apps (e.g. LibreLink). Tags are forwarded to [NfcControlActivity].
 *
 * Lifecycle: call [onResume], [onPause], and [onNewIntent] from the host activity.
 * Call [observeWarning] once during setup to show a dialog when the setting is enabled.
 */
class NfcForegroundDispatch(
    private val activity: Activity,
    private val preferences: Preferences,
) {
    private val adapter: NfcAdapter? by lazy {
        (activity.getSystemService(NfcManager::class.java))?.defaultAdapter
    }
    private var enabled = false

    fun onResume() {
        if (!preferences.get(BooleanKey.NfcForegroundPriority)) return
        val adapter = adapter ?: return
        val pendingIntent = PendingIntent.getActivity(
            activity, 0,
            Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
        )
        adapter.enableForegroundDispatch(activity, pendingIntent, null, null)
        enabled = true
    }

    fun onPause() {
        if (!enabled) return
        adapter?.disableForegroundDispatch(activity)
        enabled = false
    }

    fun onNewIntent(intent: Intent) {
        val action = intent.action ?: return
        if (action != NfcAdapter.ACTION_NDEF_DISCOVERED && action != NfcAdapter.ACTION_TAG_DISCOVERED) return
        activity.startActivity(Intent(activity, NfcControlActivity::class.java).apply {
            this.action = action
            intent.extras?.let { putExtras(it) }
        })
    }

    fun observeWarning(scope: CoroutineScope, rxBus: RxBus, rh: ResourceHelper) {
        scope.launch {
            preferences.observe(BooleanKey.NfcForegroundPriority).drop(1).collect { enabled ->
                if (enabled) {
                    rxBus.send(
                        EventShowDialog.Ok(
                            title = rh.gs(R.string.nfc_foreground_priority_warning_title),
                            message = rh.gs(R.string.nfc_foreground_priority_warning_message),
                        )
                    )
                }
            }
        }
    }
}
