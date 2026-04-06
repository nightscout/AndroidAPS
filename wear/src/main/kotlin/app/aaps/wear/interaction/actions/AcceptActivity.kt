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
        val isError = extras?.getBoolean(DataLayerListenerServiceWear.KEY_IS_ERROR, false) ?: false
        val tempTargetLow = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_TEMP_TARGET_LOW)) it.getDouble(DataLayerListenerServiceWear.KEY_TEMP_TARGET_LOW) else null }
        val tempTargetHigh = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_TEMP_TARGET_HIGH)) it.getDouble(DataLayerListenerServiceWear.KEY_TEMP_TARGET_HIGH) else null }
        val tempTargetDuration = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_TEMP_TARGET_DURATION)) it.getInt(DataLayerListenerServiceWear.KEY_TEMP_TARGET_DURATION) else null }
        val tempTargetIsMGDL = extras?.getBoolean(DataLayerListenerServiceWear.KEY_TEMP_TARGET_IS_MGDL, true) ?: true
        val isCancelTempTarget = extras?.getBoolean(DataLayerListenerServiceWear.KEY_CANCEL_TEMP_TARGET, false) ?: false
        val tempTargetReason = extras?.getString(DataLayerListenerServiceWear.KEY_TEMP_TARGET_REASON)
        val profileName = extras?.getString(DataLayerListenerServiceWear.KEY_PROFILE_NAME)
        val profilePercentage = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_PROFILE_PERCENTAGE)) it.getInt(DataLayerListenerServiceWear.KEY_PROFILE_PERCENTAGE) else null }
        val profileTimeshift = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_PROFILE_TIMESHIFT)) it.getInt(DataLayerListenerServiceWear.KEY_PROFILE_TIMESHIFT) else null }
        val profileDuration = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_PROFILE_DURATION)) it.getInt(DataLayerListenerServiceWear.KEY_PROFILE_DURATION) else null }
        val runningModeTitle = extras?.getString(DataLayerListenerServiceWear.KEY_RUNNING_MODE_TITLE)
        val runningModeDuration = extras?.let { if (it.containsKey(DataLayerListenerServiceWear.KEY_RUNNING_MODE_DURATION_MINUTES)) it.getInt(DataLayerListenerServiceWear.KEY_RUNNING_MODE_DURATION_MINUTES) else null }
        val runningModeType = extras?.getString(DataLayerListenerServiceWear.KEY_RUNNING_MODE_TYPE)

        val hasTempTargetData = isCancelTempTarget || tempTargetDuration != null
        val hasProfileData = profileName != null
        val hasRunningModeData = runningModeTitle != null

        if (message.isEmpty() && insulin == null && carbs == null && !hasTempTargetData && !hasProfileData && !hasRunningModeData) {
            finish()
            return
        }

        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50), -1))

        val hasStructuredData = insulin != null || carbs != null
        val hasAnyStructuredSummary = hasStructuredData || hasTempTargetData || hasProfileData || hasRunningModeData
        val fmt = DecimalFormat("#0.0")
        val ttFmt = if (tempTargetIsMGDL) DecimalFormat("0") else DecimalFormat("#0.0")
        val ttUnit = if (tempTargetIsMGDL) "mg/dL" else "mmol/L"

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
                val pagerState = rememberPagerState(pageCount = { if (isError && !hasAnyStructuredSummary) 1 else 2 })

                LaunchedEffect(Unit) {
                    delay(60_000)
                    finish()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0    -> {
                                val curvedTitle = when {
                                    hasStructuredData  -> stringResource(R.string.menu_treatment)
                                    hasTempTargetData  -> stringResource(R.string.loop_status_temp_target)
                                    hasProfileData     -> stringResource(R.string.status_profile_switch)
                                    hasRunningModeData -> stringResource(R.string.status_running_mode)
                                    else               -> null
                                }
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (curvedTitle != null) CurvedTitle(curvedTitle)
                                    if (hasStructuredData) {
                                    // Bolus / Carbs / eCarbs structured summary
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
                                } else if (hasTempTargetData) {
                                    // TempTarget structured summary
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
                                        if (isCancelTempTarget) {
                                            Text(
                                                text = message,
                                                color = WearSecondaryText,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                            )
                                        } else if (tempTargetLow != null && tempTargetHigh != null && tempTargetDuration != null) {
                                            val isRange = tempTargetLow != tempTargetHigh
                                            val targetText = if (!isRange)
                                                "${ttFmt.format(tempTargetLow)} $ttUnit"
                                            else
                                                "${ttFmt.format(tempTargetLow)} \u2013 ${ttFmt.format(tempTargetHigh)} $ttUnit"
                                            Text(
                                                text = targetText,
                                                color = TempTargetYellow,
                                                fontSize = if (isRange) 17.sp else 20.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            Text(
                                                text = stringResource(R.string.action_confirm_duration, formatDurationMinutes(tempTargetDuration)),
                                                color = WearSecondaryText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                            if (tempTargetReason != null) {
                                                Text(
                                                    text = stringResource(R.string.action_confirm_reason, tempTargetReason),
                                                    color = WearSecondaryText,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                } else if (hasProfileData) {
                                    // ProfileSwitch structured summary
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
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = profileName,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                        )
                                        if (profilePercentage != null) {
                                            Text(
                                                text = "$profilePercentage%",
                                                color = Color.White,
                                                fontSize = 20.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (profileTimeshift != null) {
                                            val tsText = "${if (profileTimeshift > 0) "+" else ""}${stringResource(R.string.action_duration_hours_format, profileTimeshift)}"
                                            Text(
                                                text = stringResource(R.string.action_confirm_timeshift, tsText),
                                                color = WearSecondaryText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        if (profileDuration != null) {
                                            val durText = if (profileDuration == 0) "\u221E" else formatDurationMinutes(profileDuration)
                                            Text(
                                                text = stringResource(R.string.action_confirm_duration, durText),
                                                color = WearSecondaryText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                } else if (hasRunningModeData) {
                                    // RunningMode structured summary
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
                                        val stateColor = when (runningModeType) {
                                            "LOOP_CLOSED"       -> LoopClosedColor
                                            "LOOP_OPEN"         -> LoopOpenColor
                                            "LOOP_LGS"          -> LoopLgsColor
                                            "LOOP_USER_SUSPEND" -> LoopSuspendedColor
                                            "LOOP_DISABLE"      -> LoopDisabledColor
                                            "PUMP_DISCONNECT"   -> LoopDisconnectedColor
                                            "SUPERBOLUS"        -> LoopSuperbolusColor
                                            "LOOP_RESUME",
                                            "PUMP_RECONNECT"    -> LoopClosedColor
                                            else                -> ConfirmGreen
                                        }
                                        Text(
                                            text = runningModeTitle,
                                            color = stateColor,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center,
                                        )
                                        if (runningModeDuration != null) {
                                            Text(
                                                text = stringResource(R.string.action_confirm_duration, formatDurationMinutes(runningModeDuration)),
                                                color = WearSecondaryText,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
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
                    if (!isError) HorizontalPageIndicator(
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
