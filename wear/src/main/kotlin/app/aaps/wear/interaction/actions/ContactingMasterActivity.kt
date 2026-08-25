package app.aaps.wear.interaction.actions

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.wear.R
import kotlinx.coroutines.delay

/**
 * Transient "Contacting master…" spinner shown while a watch-on-client insulin action round-trips to the master
 * (prepare gap before the confirmation lines, and the commit gap after ✓ before delivery is acked). It is dismissed
 * by [DataHandlerWear] when the resolving terminal arrives (the confirmation/error [app.aaps.core.interfaces.rx.weardata.EventData.ConfirmAction]
 * or [app.aaps.core.interfaces.rx.weardata.EventData.RemoteDelivered]), or by its own timeout → a definitive
 * "state unknown" so it never spins forever. Single-instance: [show] is a no-op while one is already up.
 */
class ContactingMasterActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Race guard: a dismiss() that arrived between show()'s startActivity and this onCreate cleared `pending`.
        // In that case the terminal already resolved → finish immediately instead of spinning until the timeout.
        if (!pending) {
            finish()
            return
        }
        current = this
        // The activity now exists; the `current != null` guard takes over from `pending` for re-entry protection.
        pending = false

        setContent {
            MaterialTheme {
                // After the round-trip TTL + margin with no terminal, stop implying progress and tell the user the
                // outcome is unknown (they must verify on the phone/NS, never assume it didn't deliver).
                var unknown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) {
                    delay(TIMEOUT_MS)
                    unknown = true
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        if (!unknown) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.contacting_master),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.contacting_master_unknown),
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (current === this) {
            current = null
            // Covers a self-timeout finish (which does not go through dismiss()) so a later show() is not blocked.
            pending = false
        }
    }

    companion object {

        private const val TIMEOUT_MS = 20_000L

        // Same-process single-instance handle so the Data-Layer terminal handlers can dismiss the spinner.
        @Volatile private var current: ContactingMasterActivity? = null

        // Set by show() before the activity actually launches, cleared by dismiss(). Closes the RemoteDelivered race
        // where a terminal (dismiss) arrives before the spinner's onCreate runs: onCreate reads this and self-finishes.
        @Volatile private var pending = false

        /** Show the spinner (no-op if one is already up — the prepare spinner stays through to the lines screen). */
        fun show(context: Context) {
            if (current != null || pending) return
            pending = true
            context.startActivity(Intent(context, ContactingMasterActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }

        /** Dismiss the spinner when a terminal arrives (cancels a still-pending show, or no-ops if never shown). */
        fun dismiss() {
            pending = false
            current?.finish()
            current = null
        }
    }
}
