package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
import java.text.DecimalFormat

class AcceptActivity : DaggerAppCompatActivity() {

    private var actionKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        val message = extras?.getString(DataLayerListenerServiceWear.KEY_MESSAGE, "") ?: ""
        actionKey = extras?.getString(DataLayerListenerServiceWear.KEY_ACTION_DATA, "") ?: ""

        val insulin = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_INSULIN)) it.getDouble(DataLayerListenerServiceWear.KEY_INSULIN) else null }
        val carbs = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_CARBS)) it.getInt(DataLayerListenerServiceWear.KEY_CARBS) else null }
        val carbsTimeShift = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_CARBS_TIME_SHIFT)) it.getInt(DataLayerListenerServiceWear.KEY_CARBS_TIME_SHIFT) else null }
        val duration = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_DURATION)) it.getInt(DataLayerListenerServiceWear.KEY_DURATION) else null }
        val constraintApplied = extras?.getBoolean(DataLayerListenerServiceWear.KEY_CONSTRAINT_APPLIED, false) ?: false

        if (message.isEmpty() && insulin == null && carbs == null) {
            finish()
            return
        }

        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50), -1))

        val hasStructuredData = insulin != null || carbs != null
        val fmt = DecimalFormat("#0.0")

        // Pre-compute eCarbs time display string
        val timeDisplayStr: String? = if (carbsTimeShift != null && carbsTimeShift != 0) {
            val startMs = System.currentTimeMillis() + carbsTimeShift * 60_000L
            val nowCal = Calendar.getInstance()
            val startCal = Calendar.getInstance().apply { timeInMillis = startMs }
            val isSameDay = nowCal.get(Calendar.DAY_OF_YEAR) == startCal.get(Calendar.DAY_OF_YEAR) &&
                nowCal.get(Calendar.YEAR) == startCal.get(Calendar.YEAR)
            if (isSameDay) SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(startMs))
            else SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(startMs))
        } else null

        setContent {
            MaterialTheme {
                val pagerState = rememberPagerState(pageCount = { 2 })

                LaunchedEffect(Unit) {
                    delay(60_000)
                    finish()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0    -> {
                                if (hasStructuredData) {
                                    // Rich structured summary
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp, vertical = 16.dp),
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
                                        if (insulin != null && insulin > 0.0) {
                                            Text(
                                                text = "${fmt.format(insulin)} ${stringResource(R.string.insulin_unit_short)}",
                                                color = InsulinBlue,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (carbs != null && carbs != 0) {
                                            Text(
                                                text = stringResource(R.string.wizard_carbs_format, carbs),
                                                color = CarbsOrange,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (carbsTimeShift != null && carbsTimeShift != 0 && timeDisplayStr != null) {
                                            Text(
                                                text = stringResource(R.string.ecarbs_confirm_time, timeDisplayStr),
                                                color = WearSecondaryText,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (duration != null && duration != 0) {
                                            Text(
                                                text = stringResource(R.string.ecarbs_confirm_duration, duration),
                                                color = WearSecondaryText,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (constraintApplied) {
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                text = stringResource(R.string.constraint_applied),
                                                color = Color(0xFFFFB300),
                                                fontSize = 14.sp,
                                                textAlign = TextAlign.Center,
                                            )
                                        }
                                    }
                                } else {
                                    // Fallback: plain text message (non-migrated activities)
                                    val scrollState = rememberScrollState()
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(scrollState)
                                            .padding(horizontal = 24.dp, vertical = 16.dp),
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        Text(
                                            text = message,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            textAlign = TextAlign.Start,
                                        )
                                    }
                                }
                            }

                            else -> {
                                // Confirm page
                                var confirmationSent by remember { mutableStateOf(false) }
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
                    HorizontalPageIndicator(
                        pagerState = pagerState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp),
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
        startActivity(
            Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.wizard_confirmation_sent))
            }
        )
        finish()
    }
}
