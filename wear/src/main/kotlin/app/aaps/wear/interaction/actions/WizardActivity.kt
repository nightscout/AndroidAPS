package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
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
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.model.CarbsType
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData.ActionWizardPreCheck
import app.aaps.core.interfaces.sharedPreferences.SP
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.wear.R
import app.aaps.wear.di.WearMetroActivity
import dev.zacsweers.metro.Inject

/** The carb time page: the phone wizard's range and step. */
private val CARB_TIME_RANGE = -60.0..60.0
private const val CARB_TIME_STEP = 5.0

class WizardActivity : WearMetroActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var preferences: Preferences
    @Inject lateinit var sp: SP

    /** The pages of the wizard, in order. Three of them are settings, so the list is built once per start. */
    private enum class Page { CARBS, CARBS_TYPE, CARB_TIME, PERCENTAGE, CONFIRM }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val hasCarbsType = sp.getBoolean(getString(R.string.key_wizard_carbs_type), false)
        val hasCarbTime = sp.getBoolean(getString(R.string.key_wizard_carb_time), false)
        val hasPercentage = sp.getBoolean(getString(R.string.key_wizard_percentage), false)
        val pages = buildList {
            add(Page.CARBS)
            if (hasCarbsType) add(Page.CARBS_TYPE)
            if (hasCarbTime) add(Page.CARB_TIME)
            if (hasPercentage) add(Page.PERCENTAGE)
            add(Page.CONFIRM)
        }
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
                var carbsType by remember { mutableStateOf(CarbsType.BREAD) }
                // Minutes from now until the carbs are eaten, as on the phone. The alarm is the "time to eat"
                // timer, on by default; it only counts when the carbs come later
                var carbTime by remember { mutableStateOf(0.0) }
                var alarm by remember { mutableStateOf(true) }
                var percentage by remember { mutableStateOf(defaultPercentage) }
                val pagerState = rememberPagerState(pageCount = { pages.size })

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (pages[page]) {
                            Page.CARBS      -> PlusMinusInputScreen(
                                value = carbs,
                                onValueChange = { carbs = it },
                                valueRange = 0.0..maxCarbs,
                                stepValues = carbStepValues,
                                format = NumberFormat.INTEGER,
                                label = stringResource(R.string.action_carbs_gram),
                                allowZero = false,
                                isActive = pagerState.currentPage == page,
                                enabled = !pagerState.isScrollInProgress,
                                valueColor = CarbsOrange,
                                title = stringResource(R.string.menu_wizard),
                            )
                            Page.CARBS_TYPE -> CarbsTypeScreen(
                                carbs = carbs.toInt(),
                                selected = carbsType,
                                onSelect = { carbsType = it },
                                enabled = !pagerState.isScrollInProgress,
                            )
                            Page.CARB_TIME  -> PlusMinusInputScreen(
                                value = carbTime,
                                onValueChange = { carbTime = it },
                                valueRange = CARB_TIME_RANGE,
                                stepValues = listOf(CARB_TIME_STEP, CARB_TIME_STEP, CARB_TIME_STEP),
                                format = NumberFormat.INTEGER,
                                label = stringResource(R.string.wizard_carb_time),
                                displayText = if (carbTime == 0.0) stringResource(R.string.wizard_carb_time_now) else stringResource(R.string.wizard_carb_time_offset, carbTime.toInt()),
                                allowZero = true,
                                isActive = pagerState.currentPage == page,
                                simpleMode = true,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.menu_wizard),
                                // Always in the layout, so the value does not jump when the bell comes and goes
                                footer = { AlarmToggle(checked = alarm, onToggle = { alarm = it }, visible = carbTime > 0, enabled = !pagerState.isScrollInProgress) },
                            )
                            Page.PERCENTAGE -> PlusMinusInputScreen(
                                value = percentage,
                                onValueChange = { percentage = it },
                                valueRange = Constants.WIZARD_PERCENTAGE_RANGE,
                                stepValues = listOf(5.0, 5.0, 5.0),
                                format = NumberFormat.INTEGER,
                                label = stringResource(R.string.action_percentage),
                                allowZero = true,
                                isActive = pagerState.currentPage == page,
                                simpleMode = true,
                                enabled = !pagerState.isScrollInProgress,
                                title = stringResource(R.string.menu_wizard),
                            )
                            Page.CONFIRM    -> WizardConfirmScreen(
                                carbs = carbs.toInt(),
                                carbsType = if (hasCarbsType) carbsType else null,
                                carbTime = if (hasCarbTime) carbTime.toInt() else 0,
                                alarm = alarm,
                                percentage = if (hasPercentage) percentage.toInt() else null,
                                onConfirm = {
                                    confirmWizard(carbs.toInt(), percentage.toInt(), carbsType, if (hasCarbTime) carbTime.toInt() else 0, alarm)
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

    private fun confirmWizard(carbs: Int, percentage: Int, carbsType: CarbsType, carbTime: Int, alarm: Boolean) {
        rxBus.send(EventWearToMobile(ActionWizardPreCheck(carbs, percentage, carbsType, carbTime, alarm && carbTime > 0)))
        startActivity(
            Intent(this, ConfirmationActivity::class.java).apply {
                putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
                putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.action_wizard_confirmation))
            }
        )
        finish()
    }
}

/**
 * One tap picks the meal type, the page does not move on by itself. The grams line shows what the type does
 * with the carbs from the page before, "52+22 g", so the user sees the result before the request page.
 */
@Composable
private fun CarbsTypeScreen(carbs: Int, selected: CarbsType, onSelect: (CarbsType) -> Unit, enabled: Boolean) {
    val carbsNow = selected.carbsNow(carbs)
    val eCarbs = selected.eCarbs(carbs)
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.wizard_carbs_type),
            color = WearSecondaryText,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(12.dp))
        // Three circles with the gaps must fit a 192 dp round screen with the padding: 3 x 44 + 2 x 10 = 152 dp.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CarbsType.entries.forEach { type ->
                val isSelected = type == selected
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) CarbsOrange else Color.White.copy(alpha = 0.15f))
                        .clickable(enabled = enabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(type)
                        }
                        .semantics { this.selected = isSelected },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(type.iconRes()),
                        contentDescription = stringResource(type.nameRes()),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(selected.nameRes()),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (eCarbs > 0) stringResource(R.string.wizard_carbs_split, carbsNow, eCarbs) else stringResource(R.string.wizard_carbs_short, carbsNow),
            color = CarbsOrange,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The "time to eat" alarm of the carb time page: a bell that is lit when the alarm is on. One tap flips it.
 * Not [visible] while the carbs are not later, but still in the layout, so the page does not move.
 */
@Composable
private fun AlarmToggle(checked: Boolean, onToggle: (Boolean) -> Unit, visible: Boolean, enabled: Boolean) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .padding(top = 4.dp)
            .alpha(if (visible) 1f else 0f)
            .clip(CircleShape)
            .clickable(enabled = enabled && visible) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle(!checked)
            }
            .semantics { this.selected = checked }
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_alarm),
            contentDescription = null,
            tint = if (checked) CarbsOrange else WearSecondaryText,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = stringResource(R.string.wizard_set_alarm),
            color = if (checked) Color.White else WearSecondaryText,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

private fun CarbsType.iconRes(): Int = when (this) {
    CarbsType.BREAD -> R.drawable.ic_bread
    CarbsType.CAKE  -> R.drawable.ic_cake
    CarbsType.PIZZA -> R.drawable.ic_pizza
}

private fun CarbsType.nameRes(): Int = when (this) {
    CarbsType.BREAD -> R.string.wizard_carbs_type_bread
    CarbsType.CAKE  -> R.string.wizard_carbs_type_cake
    CarbsType.PIZZA -> R.string.wizard_carbs_type_pizza
}

/**
 * [carbsType] is null when the meal type page is off. Then the carbs line is the whole entry, as before.
 * [carbTime] is shown when it is not zero, with the bell when the alarm will be set.
 */
@Composable
private fun WizardConfirmScreen(carbs: Int, carbsType: CarbsType?, carbTime: Int, alarm: Boolean, percentage: Int?, onConfirm: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var confirmationSent by remember { mutableStateOf(false) }
    val carbsNow = carbsType?.carbsNow(carbs) ?: carbs
    val eCarbs = carbsType?.eCarbs(carbs) ?: 0

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
        // One line like the phone wizard, "52+22 g". The result page shows the eCarbs time.
        Text(
            text = if (eCarbs > 0) stringResource(R.string.wizard_carbs_split, carbsNow, eCarbs) else stringResource(R.string.wizard_carbs_short, carbsNow),
            color = CarbsOrange,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (carbTime != 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.wizard_result_carb_time, carbTime),
                    color = WearSecondaryText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                if (alarm && carbTime > 0) {
                    Spacer(Modifier.size(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_alarm),
                        contentDescription = stringResource(R.string.wizard_set_alarm),
                        tint = CarbsOrange,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
        if (percentage != null) {
            Text(
                text = stringResource(R.string.wizard_result_correction_percentage, percentage),
                color = WearSecondaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
