package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.activity.ConfirmationActivity
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionTempTargetPreCheck
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.wear.R
import dagger.android.support.DaggerAppCompatActivity
import java.text.DecimalFormat
import javax.inject.Inject

class TempTargetActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var sp: SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isMGDL = sp.getBoolean(R.string.key_units_mgdl, true)
        val isSingleTarget = sp.getBoolean(R.string.key_single_target, true)

        val targetSteps = if (isMGDL) listOf(1.0, 5.0, 10.0) else listOf(0.1, 0.5, 1.0)
        val targetFormat = if (isMGDL) DecimalFormat("0") else DecimalFormat("#0.0")
        val targetMin = if (isMGDL) 72.0 else 4.0
        val targetMax = if (isMGDL) 180.0 else 10.0
        val targetDefault = if (isMGDL) 101.0 else 5.6
        val unit = if (isMGDL) "mg/dL" else "mmol/L"
        val pageCount = if (isSingleTarget) 3 else 4

        setContent {
            MaterialTheme {
                var duration by remember { mutableStateOf(60.0) }
                var low by remember { mutableStateOf(targetDefault) }
                var high by remember { mutableStateOf(targetDefault) }
                val pagerState = rememberPagerState(pageCount = { pageCount })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when {
                            page == 0                    -> PlusMinusInputScreen(
                                value = duration,
                                onValueChange = { duration = it },
                                min = 0.0,
                                max = 24.0 * 60.0,
                                stepValues = listOf(5.0, 30.0, 60.0),
                                format = DecimalFormat("0"),
                                displayText = formatDurationMinutes(duration.toInt()),
                                label = stringResource(R.string.loop_status_duration),
                                allowZero = true,
                                isActive = pagerState.currentPage == 0,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.loop_status_temp_target),
                            )
                            page == 1                    -> PlusMinusInputScreen(
                                value = low,
                                onValueChange = { low = it },
                                min = targetMin,
                                max = targetMax,
                                stepValues = targetSteps,
                                format = targetFormat,
                                label = stringResource(
                                    if (isSingleTarget) R.string.action_target_unit else R.string.action_low_unit,
                                    unit
                                ),
                                allowZero = false,
                                isActive = pagerState.currentPage == 1,
                                enabled = !pagerState.isScrollInProgress,
                                valueColor = TempTargetYellow,
                                title = stringResource(R.string.loop_status_temp_target),
                            )
                            page == 2 && !isSingleTarget -> PlusMinusInputScreen(
                                value = high,
                                onValueChange = { high = it },
                                min = targetMin,
                                max = targetMax,
                                stepValues = targetSteps,
                                format = targetFormat,
                                label = stringResource(R.string.action_high_unit, unit),
                                allowZero = false,
                                isActive = pagerState.currentPage == 2,
                                enabled = !pagerState.isScrollInProgress,
                                valueColor = TempTargetYellow,
                                title = stringResource(R.string.loop_status_temp_target),
                            )
                            else                         -> TempTargetRequestScreen(
                                duration = duration.toInt(),
                                low = low,
                                high = if (isSingleTarget) low else high,
                                isMGDL = isMGDL,
                                onConfirm = {
                                    confirmTempTarget(
                                        isMGDL, duration.toInt(), low,
                                        if (isSingleTarget) low else high
                                    )
                                },
                            )
                        }
                    }
                    HorizontalPageIndicator(
                        pagerState = pagerState,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                    )
                }
            }
        }
    }

    private fun confirmTempTarget(isMGDL: Boolean, duration: Int, low: Double, high: Double) {
        rxBus.send(EventWearToMobile(ActionTempTargetPreCheck(ActionTempTargetPreCheck.TempTargetCommand.MANUAL, isMGDL, duration, low, high)))
        startActivity(
            Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.action_tempt_confirmation))
            }
        )
        finish()
    }
}

@Composable
private fun TempTargetRequestScreen(
    duration: Int,
    low: Double,
    high: Double,
    isMGDL: Boolean,
    onConfirm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var confirmationSent by remember { mutableStateOf(false) }
    val format = if (isMGDL) DecimalFormat("0") else DecimalFormat("#0.0")
    val unit = if (isMGDL) "mg/dL" else "mmol/L"
    val isRange = low != high
    val targetText = if (!isRange) "${format.format(low)} $unit"
    else "${format.format(low)} \u2013 ${format.format(high)} $unit"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.request),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .clickable(enabled = !confirmationSent) {
                    confirmationSent = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_confirm),
                contentDescription = stringResource(R.string.confirm),
                tint = ConfirmGreen,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = targetText,
            color = TempTargetYellow,
            fontSize = if (isRange) 17.sp else 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = formatDurationMinutes(duration),
            color = WearSecondaryText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

