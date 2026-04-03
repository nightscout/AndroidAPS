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
import app.aaps.core.interfaces.rx.weardata.EventData.ActionBolusPreCheck
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import dagger.android.support.DaggerAppCompatActivity
import java.text.DecimalFormat
import javax.inject.Inject
import kotlin.math.roundToInt

class TreatmentActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var sp: SP

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incrementInsulin1 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement1) * 10).roundToInt() / 10.0
        val incrementInsulin2 = (preferences.get(DoubleKey.OverviewInsulinButtonIncrement2) * 10).roundToInt() / 10.0
        val stepValuesInsulin = listOf(0.1, incrementInsulin1, incrementInsulin2)

        val incrementCarbs1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble()
        val incrementCarbs2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble()
        val stepValuesCarbs = listOf(1.0, incrementCarbs1, incrementCarbs2)

        val maxBolus = sp.getDouble(getString(R.string.key_treatments_safety_max_bolus), 3.0)
        val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()

        setContent {
            MaterialTheme {
                var insulin by remember { mutableStateOf(0.0) }
                var carbs by remember { mutableStateOf(0.0) }
                val pagerState = rememberPagerState(pageCount = { 3 })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                    ) { page ->
                        when (page) {
                            0 -> PlusMinusInputScreen(
                                value = insulin,
                                onValueChange = { insulin = it },
                                min = 0.0,
                                max = maxBolus,
                                stepValues = stepValuesInsulin,
                                format = DecimalFormat("#0.0"),
                                label = stringResource(R.string.action_insulin_units),
                                allowZero = false,
                                isActive = pagerState.currentPage == 0,
                                enabled = !pagerState.isScrollInProgress,
                                valueColor = InsulinBlue,
                                title = stringResource(R.string.menu_treatment),
                            )
                            1 -> PlusMinusInputScreen(
                                value = carbs,
                                onValueChange = { carbs = it },
                                min = -maxCarbs,
                                max = maxCarbs,
                                stepValues = stepValuesCarbs,
                                format = DecimalFormat("0"),
                                label = stringResource(R.string.action_carbs_gram),
                                allowZero = false,
                                isActive = pagerState.currentPage == 1,
                                enabled = !pagerState.isScrollInProgress,
                                valueColor = CarbsOrange,
                                title = stringResource(R.string.menu_treatment),
                            )
                            else -> TreatmentConfirmScreen(
                                insulin = insulin,
                                carbs = carbs.toInt(),
                                onConfirm = { confirmTreatment(insulin, carbs.toInt()) },
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

    private fun confirmTreatment(insulin: Double, carbs: Int) {
        rxBus.send(EventWearToMobile(ActionBolusPreCheck(insulin, carbs)))
        startActivity(
            Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.action_treatment_confirmation))
            }
        )
        finish()
    }
}

@Composable
private fun TreatmentConfirmScreen(insulin: Double, carbs: Int, onConfirm: () -> Unit) {
    val fmt = remember { DecimalFormat("#0.0") }
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
        if (insulin > 0.0) {
            Text(
                text = "${fmt.format(insulin)} ${stringResource(R.string.insulin_unit_short)}",
                color = InsulinBlue,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (carbs != 0) {
            Text(
                text = stringResource(R.string.wizard_carbs_format, carbs),
                color = CarbsOrange,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
