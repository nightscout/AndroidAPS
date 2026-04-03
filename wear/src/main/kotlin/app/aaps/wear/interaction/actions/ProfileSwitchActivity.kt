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
import app.aaps.core.data.configuration.Constants
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionProfileSwitchPreCheck
import app.aaps.wear.R
import dagger.android.support.DaggerAppCompatActivity
import java.text.DecimalFormat
import javax.inject.Inject

class ProfileSwitchActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val percentage = intent.extras?.getInt("percentage", -1) ?: -1
        val timeshift = intent.extras?.getInt("timeshift", -25) ?: -25
        if (percentage == -1 || timeshift == -25) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                var currentTimeshift by remember { mutableStateOf(timeshift.toDouble()) }
                var currentPercentage by remember { mutableStateOf(percentage.toDouble()) }
                var currentDuration by remember { mutableStateOf(0.0) }
                val pagerState = rememberPagerState(pageCount = { 4 })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0    -> PlusMinusInputScreen(
                                value = currentTimeshift,
                                onValueChange = { currentTimeshift = it },
                                min = Constants.CPP_MIN_TIMESHIFT.toDouble(),
                                max = Constants.CPP_MAX_TIMESHIFT.toDouble(),
                                stepValues = listOf(1.0, 1.0, 1.0),
                                format = DecimalFormat("0"),
                                label = stringResource(R.string.action_timeshift_hours),
                                allowZero = false,
                                isActive = pagerState.currentPage == 0,
                                simpleMode = true,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.status_profile_switch),
                            )
                            1    -> PlusMinusInputScreen(
                                value = currentPercentage,
                                onValueChange = { currentPercentage = it },
                                min = Constants.CPP_MIN_PERCENTAGE.toDouble(),
                                max = Constants.CPP_MAX_PERCENTAGE.toDouble(),
                                stepValues = listOf(5.0, 20.0, 20.0),
                                format = DecimalFormat("0"),
                                label = stringResource(R.string.action_percentage),
                                allowZero = false,
                                isActive = pagerState.currentPage == 1,
                                simpleMode = true,
                                symmetricLargeSteps = true,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.status_profile_switch),
                            )
                            2    -> PlusMinusInputScreen(
                                value = currentDuration,
                                onValueChange = { currentDuration = it },
                                min = 0.0,
                                max = Constants.MAX_PROFILE_SWITCH_DURATION,
                                stepValues = listOf(10.0, 60.0, 240.0),
                                format = DecimalFormat("0"),
                                displayText = if (currentDuration == 0.0) "\u221E" else formatDurationMinutes(currentDuration.toInt()),
                                label = stringResource(R.string.loop_status_duration),
                                allowZero = false,
                                isActive = pagerState.currentPage == 2,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.status_profile_switch),
                            )
                            else -> ProfileSwitchRequestScreen(
                                timeshift = currentTimeshift.toInt(),
                                percentage = currentPercentage.toInt(),
                                duration = currentDuration.toInt(),
                                onConfirm = {
                                    confirmProfileSwitch(
                                        currentTimeshift.toInt(),
                                        currentPercentage.toInt(),
                                        currentDuration.toInt()
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

    private fun confirmProfileSwitch(timeshift: Int, percentage: Int, duration: Int) {
        rxBus.send(EventWearToMobile(ActionProfileSwitchPreCheck(timeshift, percentage, duration)))
        startActivity(
            Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.action_profile_switch_confirmation))
            }
        )
        finish()
    }
}

@Composable
private fun ProfileSwitchRequestScreen(
    timeshift: Int,
    percentage: Int,
    duration: Int,
    onConfirm: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var confirmationSent by remember { mutableStateOf(false) }

    val timeshiftText = "${if (timeshift > 0) "+" else ""}${stringResource(R.string.action_duration_hours_format, timeshift)}"
    val durationText = if (duration == 0) "\u221E" else formatDurationMinutes(duration)
    val detailLine = "$timeshiftText / $durationText"

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
            text = "$percentage%",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (detailLine.isNotEmpty()) {
            Text(
                text = detailLine,
                color = WearSecondaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
