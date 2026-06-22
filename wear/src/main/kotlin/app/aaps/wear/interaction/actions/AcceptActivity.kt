package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.comm.IntentCancelNotification
import app.aaps.wear.comm.IntentWearToMobile
import dagger.android.support.DaggerAppCompatActivity
import kotlinx.coroutines.delay

/**
 * Two-page confirmation screen for an action authored on (or relayed by) the master.
 *
 * Page 0 shows either the master-authored [ConfirmationLine] rows (role + already-localized text, rendered
 * verbatim) under an optional curved [title], or — for errors / non-migrated activities — a plain text
 * message. Page 1 is the confirm page: a single ✓ that fires [confirm] once. An error with no lines collapses
 * to a single (message-only) page.
 *
 * deferConfirm → spinner contract: when [deferConfirm] is set the commit is a CLIENT→master round-trip, so ✓
 * must NOT flash a local success animation. Instead it shows the [ContactingMasterActivity] spinner and waits
 * for the real terminal (a [app.aaps.core.interfaces.rx.weardata.EventData.RemoteDelivered] success or an error
 * [app.aaps.core.interfaces.rx.weardata.EventData.ConfirmAction]), both of which dismiss the spinner.
 *
 * A 60s [LaunchedEffect] auto-dismisses the screen if the user never acts. It is also cancelled implicitly by
 * [onPause] (which finishes the activity), so backgrounding the screen tears it down rather than leaving a stale
 * confirmation alive in the background.
 */
class AcceptActivity : DaggerAppCompatActivity() {

    private var actionKey = ""

    private var deferConfirm = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // This confirm/error screen IS the resolved terminal of any in-flight prepare round-trip → dismiss its spinner.
        ContactingMasterActivity.dismiss()

        val extras = intent.extras
        val message = extras?.getString(DataLayerListenerServiceWear.KEY_MESSAGE, "") ?: ""
        actionKey = extras?.getString(DataLayerListenerServiceWear.KEY_ACTION_DATA, "") ?: ""
        // CLIENT relay: the commit is a master round-trip → don't flash local success on ✓; show the spinner + wait.
        deferConfirm = extras?.getBoolean(DataLayerListenerServiceWear.KEY_DEFER_CONFIRM, false) ?: false

        // Master-authored confirmation rows (role name + text), rendered verbatim. Non-empty for bolus / eCarbs.
        val lineRoles = extras?.getStringArray(DataLayerListenerServiceWear.KEY_LINE_ROLES)
        val lineTexts = extras?.getStringArray(DataLayerListenerServiceWear.KEY_LINE_TEXTS)
        val lines: List<Pair<String, String>> =
            if (lineRoles != null && lineTexts != null && lineRoles.size == lineTexts.size)
                lineRoles.indices.map { lineRoles[it] to lineTexts[it] }
            else emptyList()

        val isError = extras?.getBoolean(DataLayerListenerServiceWear.KEY_IS_ERROR, false) ?: false
        // Curved header for the lines screen (e.g. "Treatment" / "Temporary target" / "Running mode"), authored by the master.
        val title = extras?.getString(DataLayerListenerServiceWear.KEY_TITLE, "") ?: ""

        if (message.isEmpty() && lines.isEmpty() && !isError) {
            finish()
            return
        }

        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50), -1))

        val hasLines = lines.isNotEmpty()

        setContent {
            MaterialTheme {
                val pagerState = rememberPagerState(pageCount = { if (isError) 1 else 2 })
                // Hoisted above the pager pages so a page recomposition (e.g. swiping back to page 0) can never
                // reset it and re-enable a second ✓ tap after the first already fired.
                var confirmationSent by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    delay(60_000)
                    finish()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0    -> {
                                val curvedTitle = if (hasLines) title.ifEmpty { null } else null
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (curvedTitle != null) CurvedTitle(curvedTitle)
                                    if (hasLines) {
                                        // Master-authored confirmation rows — rendered verbatim, scrollable
                                        // (confirm lives on pager page 1, so scrolling here never blocks it).
                                        val linesScroll = rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(linesScroll)
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            // Only show the flat "Confirm" header when there is no curved title; the
                                            // curved title already heads this page (and the confirm page keeps its own).
                                            if (curvedTitle == null) {
                                                Text(
                                                    text = stringResource(R.string.confirm),
                                                    color = Color.White,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                            }
                                            lines.forEach { (role, text) ->
                                                Text(
                                                    text = text,
                                                    // Aligned with master ConfirmationRole (core.data.ui.ConfirmationRole):
                                                    // NORMAL/PRIMARY have no dedicated wear color → white.
                                                    color = when (role) {
                                                        "BOLUS"             -> InsulinBlue
                                                        "CARBS", "COB"      -> CarbsOrange
                                                        "WARNING"           -> WearWarningAmber
                                                        "INFO"              -> WearSecondaryText
                                                        "TEMP_TARGET"       -> TempTargetYellow
                                                        "LOOP_CLOSED"       -> LoopClosedColor
                                                        "LOOP_OPEN"         -> LoopOpenColor
                                                        "LOOP_LGS"          -> LoopLgsColor
                                                        "LOOP_SUSPENDED"    -> LoopSuspendedColor
                                                        "LOOP_DISABLED"     -> LoopDisabledColor
                                                        "LOOP_DISCONNECTED"  -> LoopDisconnectedColor
                                                        else                -> Color.White
                                                    },
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    } else {
                                        // Fallback: plain text message (errors and non-migrated activities)
                                        val scrollState = rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(scrollState)
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            if (isError) {
                                                Text(
                                                    text = stringResource(R.string.error),
                                                    color = WearInsulinNegative,
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                                Spacer(Modifier.height(8.dp))
                                            }
                                            Text(
                                                text = message,
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                textAlign = if (isError) TextAlign.Center else TextAlign.Start,
                                            )
                                        }
                                    }
                                }
                            }

                            else -> {
                                // Confirm page (confirmationSent is hoisted to the setContent scope above).
                                val haptic = LocalHapticFeedback.current
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 24.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.confirm),
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = !confirmationSent) {
                                                confirmationSent = true
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                confirm()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_confirm),
                                            contentDescription = stringResource(R.string.confirm),
                                            tint = ConfirmGreen,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!isError) HorizontalPageIndicator(
                        pagerState = pagerState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 4.dp),
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extras?.let {
            startActivity(Intent(this, AcceptActivity::class.java).apply { putExtras(it) })
            finish()
        }
    }

    private fun confirm() {
        if (actionKey.isNotEmpty()) startService(IntentWearToMobile(this, actionKey))
        startForegroundService(IntentCancelNotification(this))
        if (deferConfirm) {
            // CLIENT relay: the commit is a master round-trip in flight — do NOT show a (false) success animation.
            // Show the "contacting master" spinner; the real terminal arrives as RemoteDelivered (→ success) or an
            // error ConfirmAction, both of which dismiss the spinner.
            ContactingMasterActivity.show(this)
        } else {
            startActivity(
                Intent(this, ConfirmationActivity::class.java).apply {
                    putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                    putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.wizard_confirmation_sent))
                }
            )
        }
        finish()
    }
}
