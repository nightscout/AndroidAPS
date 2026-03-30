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
import app.aaps.core.interfaces.rx.weardata.EventData.ActionECarbsPreCheck
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import dagger.android.support.DaggerAppCompatActivity
import java.text.DecimalFormat
import javax.inject.Inject

class ECarbActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var sp: SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val carbStepValues = listOf(
            1.0,
            preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble(),
            preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble(),
        )
        val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()

        setContent {
            MaterialTheme {
                var carbs by remember { mutableStateOf(0.0) }
                var startMinutes by remember { mutableStateOf(0.0) }
                var durationHours by remember { mutableStateOf(0.0) }
                val pagerState = rememberPagerState(pageCount = { 4 })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0    -> PlusMinusInputScreen(
                                value = carbs,
                                onValueChange = { carbs = it },
                                min = 0.0,
                                max = maxCarbs,
                                stepValues = carbStepValues,
                                format = DecimalFormat("0"),
                                label = stringResource(R.string.action_carbs_gram),
                                allowZero = false,
                                isActive = pagerState.currentPage == 0,
                                enabled = !pagerState.isScrollInProgress,
                                valueColor = CarbsOrange,
                                title = stringResource(R.string.action_ecarbs),
                            )
                            1    -> PlusMinusInputScreen(
                                value = startMinutes,
                                onValueChange = { startMinutes = it },
                                min = -60.0,
                                max = 300.0,
                                stepValues = listOf(15.0, 15.0, 15.0),
                                format = DecimalFormat("0"),
                                label = stringResource(R.string.action_start_minutes),
                                allowZero = false,
                                isActive = pagerState.currentPage == 1,
                                simpleMode = true,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.action_ecarbs),
                            )
                            2    -> PlusMinusInputScreen(
                                value = durationHours,
                                onValueChange = { durationHours = it },
                                min = 0.0,
                                max = 8.0,
                                stepValues = listOf(1.0, 1.0, 1.0),
                                format = DecimalFormat("0"),
                                label = stringResource(R.string.action_duration_hours),
                                allowZero = false,
                                isActive = pagerState.currentPage == 2,
                                simpleMode = true,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.action_ecarbs),
                            )
                            else -> ECarbConfirmScreen(
                                carbs = carbs.toInt(),
                                startMinutes = startMinutes.toInt(),
                                durationHours = durationHours.toInt(),
                                onConfirm = { confirmECarbs(carbs.toInt(), startMinutes.toInt(), durationHours.toInt()) },
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

    private fun confirmECarbs(carbs: Int, startMinutes: Int, durationHours: Int) {
        rxBus.send(EventWearToMobile(ActionECarbsPreCheck(carbs, startMinutes, durationHours)))
        startActivity(
            Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.action_ecarb_confirmation))
            }
        )
        finish()
    }
}

@Composable
private fun ECarbConfirmScreen(carbs: Int, startMinutes: Int, durationHours: Int, onConfirm: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var confirmationSent by remember { mutableStateOf(false) }

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
            text = stringResource(R.string.wizard_carbs_format, carbs),
            color = CarbsOrange,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        val startStr = when {
            startMinutes > 0  -> "+${stringResource(R.string.ecarbs_start_format, startMinutes)}"
            startMinutes != 0 -> stringResource(R.string.ecarbs_start_format, startMinutes)
            else              -> ""
        }
        val durationStr = if (durationHours != 0) stringResource(R.string.action_duration_hours_format, durationHours) else ""
        val timing = listOf(startStr, durationStr).filter { it.isNotEmpty() }.joinToString(" / ")
        if (timing.isNotEmpty()) {
            Text(
                text = timing,
                color = WearSecondaryText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
