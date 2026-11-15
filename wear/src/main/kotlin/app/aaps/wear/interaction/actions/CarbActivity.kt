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
import androidx.compose.runtime.collectAsState
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
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionECarbsPreCheck
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import app.aaps.wear.data.ComplicationData
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.di.WearMetroActivity
import dev.zacsweers.metro.Inject

class CarbActivity : WearMetroActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var sp: SP
    @Inject lateinit var complicationDataRepository: ComplicationDataRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val increment1 = preferences.get(IntKey.OverviewCarbsButtonIncrement1).toDouble()
        val increment2 = preferences.get(IntKey.OverviewCarbsButtonIncrement2).toDouble()
        val stepValues = listOf(1.0, increment1, increment2)
        val maxCarbs = sp.getInt(getString(R.string.key_treatments_safety_max_carbs), 48).toDouble()

        setContent {
            MaterialTheme {
                var carbs by remember { mutableStateOf(0.0) }
                val pagerState = rememberPagerState(pageCount = { 2 })
                // Mirror the phone Carbs dialog (issue #4637): a negative entry may remove at most the carbs
                // currently on board, taken from the last Status pushed by the phone (unknown → 0, no removal).
                // The master re-clamps on prepare, so a stale value here is only cosmetic.
                val data by complicationDataRepository.complicationData.collectAsState(initial = ComplicationData())
                val cobLimit = data.statusData.cobValue.toInt().coerceAtLeast(0)

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(
                        state = pagerState,
                    ) { page ->
                        when (page) {
                            0 -> PlusMinusInputScreen(
                                value = carbs,
                                onValueChange = { carbs = it },
                                valueRange = -cobLimit.toDouble()..maxCarbs,
                                stepValues = stepValues,
                                format = NumberFormat.INTEGER,
                                label = stringResource(R.string.action_carbs_gram),
                                hint = if (carbs < 0) stringResource(R.string.carbs_removal_cob_limit, cobLimit) else null,
                                allowZero = false,
                                isActive = pagerState.currentPage == 0,
                                enabled = !pagerState.isScrollInProgress,
                                valueColor = CarbsOrange,
                                title = stringResource(R.string.action_carbs),
                            )
                            else -> CarbConfirmScreen(
                                carbs = carbs.toInt(),
                                onConfirm = { confirmCarbs(carbs.toInt()) },
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

    private fun confirmCarbs(carbs: Int) {
        rxBus.send(EventWearToMobile(ActionECarbsPreCheck(carbs, 0, 0)))
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
private fun CarbConfirmScreen(carbs: Int, onConfirm: () -> Unit) {
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
            text = stringResource(R.string.wizard_carbs_short, carbs),
            color = CarbsOrange,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
