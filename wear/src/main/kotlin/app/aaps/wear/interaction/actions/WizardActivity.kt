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
import app.aaps.core.interfaces.rx.weardata.EventData.ActionWizardPreCheck
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import dagger.android.support.DaggerAppCompatActivity
import java.text.DecimalFormat
import javax.inject.Inject

class WizardActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var sp: SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasPercentage = sp.getBoolean(getString(R.string.key_wizard_percentage), false)
        val carbStepValues = listOf(
            1.0,
            preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble(),
            preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble(),
        )
        val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()
        val defaultPercentage = sp.getInt(getString(R.string.key_bolus_wizard_percentage), 100).toDouble()

        setContent {
            MaterialTheme {
                var carbs by remember { mutableStateOf(0.0) }
                var percentage by remember { mutableStateOf(defaultPercentage) }
                val pageCount = if (hasPercentage) 3 else 2
                val pagerState = rememberPagerState(pageCount = { pageCount })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when {
                            page == 0                  -> PlusMinusInputScreen(
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
                                title = stringResource(R.string.menu_wizard),
                            )
                            hasPercentage && page == 1 -> PlusMinusInputScreen(
                                value = percentage,
                                onValueChange = { percentage = it },
                                min = 10.0,
                                max = 200.0,
                                stepValues = listOf(5.0, 5.0, 5.0),
                                format = DecimalFormat("0"),
                                label = stringResource(R.string.action_percentage),
                                allowZero = true,
                                isActive = pagerState.currentPage == 1,
                                simpleMode = true,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.menu_wizard),
                            )
                            else                       -> WizardConfirmScreen(
                                carbs = carbs.toInt(),
                                percentage = if (hasPercentage) percentage.toInt() else null,
                                onConfirm = { confirmWizard(carbs.toInt(), percentage.toInt()) },
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

    private fun confirmWizard(carbs: Int, percentage: Int) {
        rxBus.send(EventWearToMobile(ActionWizardPreCheck(carbs, percentage)))
        startActivity(
            Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.action_wizard_confirmation))
            }
        )
        finish()
    }
}

@Composable
private fun WizardConfirmScreen(carbs: Int, percentage: Int?, onConfirm: () -> Unit) {
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
        if (percentage != null) {
            Text(
                text = "$percentage%",
                color = WearSecondaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
