package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import app.aaps.wear.comm.DataLayerListenerServiceWear
import app.aaps.wear.comm.IntentCancelNotification
import app.aaps.wear.comm.IntentWearToMobile
import dagger.android.support.DaggerAppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.text.DecimalFormat

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
 * Dismiss behaviour: non-wizard flows finish immediately on [onPause] (screen off / navigated away); wizard flows
 * instead start a 30s grace-period job so a brief wrist-down doesn't destroy the result — cancelled by [onResume].
 * Non-wizard flows also have a 60s [LaunchedEffect] absolute timeout as a backstop.
 */
class AcceptActivity : DaggerAppCompatActivity() {

    private var actionKey = ""
    private var deferConfirm = false
    private var wizardBolusId: Long? = null
    private var isWizardFlow = false
    private var screenOffJob: Job? = null

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
        val wizardDetail = extras?.getString(DataLayerListenerServiceWear.KEY_WIZARD_DETAIL)
            ?.let { runCatching { Json.decodeFromString<EventData.WizardDetail>(it) }.getOrNull() }

        isWizardFlow = wizardDetail != null

        if (wizardDetail != null && actionKey.isNotEmpty()) {
            wizardBolusId = runCatching {
                (Json.decodeFromString<EventData>(actionKey) as? EventData.ActionWizardConfirmed)?.timeStamp
            }.getOrNull()
        }

        if (message.isEmpty() && lines.isEmpty() && wizardDetail == null && !isError) {
            finish()
            return
        }

        val vibrator = getSystemService(Vibrator::class.java)
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 100, 50, 100, 50), -1))

        val hasLines = lines.isNotEmpty()

        setContent {
            MaterialTheme {
                // Wizard flows (wizardDetail != null): page 0 = calculation result, page 1 = confirm.
                // Non-wizard flows: page 0 = lines, page 1 = confirm (or 1 page for error with no lines).
                // Hoisted above the pager pages so a page recomposition (e.g. swiping back to page 0) can never
                // reset it and re-enable a second ✓ tap after the first already fired.
                var confirmationSent by remember { mutableStateOf(false) }
                // Track correction as an integer step count so returning to 0 steps always yields
                // exactly 0.0 (0 * step = 0.0 in IEEE 754), avoiding FP drift from accumulated adds.
                var correctionSteps by remember { mutableStateOf(0) }
                val correctionU = if (correctionSteps == 0) 0.0 else correctionSteps * (wizardDetail?.bolusStep ?: 0.0)
                val adjustedTotal = wizardDetail?.let { (it.unclampedInsulin + correctionU).coerceAtLeast(0.0) }
                // Swipe to confirm is only meaningful when there is insulin or carbs to deliver.
                val canConfirm = adjustedTotal == null || adjustedTotal > 0.0 || (wizardDetail!!.carbs > 0)
                val pagerState = rememberPagerState(pageCount = { if (isError && !hasLines) 1 else if (canConfirm) 2 else 1 })

                if (wizardDetail == null) LaunchedEffect(Unit) {
                    delay(60_000)
                    finish()
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> if (wizardDetail != null) {
                                WizardDetailPage(wizardDetail, correctionSteps) { correctionSteps = it }
                            } else {
                                val curvedTitle = if (hasLines) title.ifEmpty { null } else null
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (curvedTitle != null) CurvedTitle(curvedTitle)
                                    if (hasLines) {
                                        val linesScroll = rememberScrollState()
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .verticalScroll(linesScroll)
                                                .padding(horizontal = 24.dp, vertical = 16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
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
                                                    color = when (role) {
                                                        "BOLUS"            -> InsulinBlue
                                                        "CARBS", "COB"     -> CarbsOrange
                                                        "WARNING"          -> WearWarningAmber
                                                        "INFO"             -> WearSecondaryText
                                                        "TEMP_TARGET"      -> TempTargetYellow
                                                        "SCENE"            -> ScenePurple
                                                        "LOOP_CLOSED"      -> LoopClosedColor
                                                        "LOOP_OPEN"        -> LoopOpenColor
                                                        "LOOP_LGS"         -> LoopLgsColor
                                                        "LOOP_SUSPENDED"   -> LoopSuspendedColor
                                                        "LOOP_DISABLED"    -> LoopDisabledColor
                                                        "LOOP_DISCONNECTED" -> LoopDisconnectedColor
                                                        else               -> Color.White
                                                    },
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                )
                                            }
                                        }
                                    } else {
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

                            else -> WizardConfirmPage(
                                enabled = !confirmationSent,
                                totalInsulin = wizardDetail?.let { (it.unclampedInsulin + correctionU).coerceAtLeast(0.0) },
                                carbs = wizardDetail?.carbs,
                                onConfirm = { confirmationSent = true; confirm(correctionU) },
                            )
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
        if (isWizardFlow) {
            screenOffJob?.cancel()
            screenOffJob = lifecycleScope.launch {
                delay(30_000)
                finish()
            }
        } else {
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        screenOffJob?.cancel()
        screenOffJob = null
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.extras?.let {
            startActivity(Intent(this, AcceptActivity::class.java).apply { putExtras(it) })
            finish()
        }
    }

    private fun confirm(correctionU: Double = 0.0) {
        val key = if (correctionU != 0.0 && wizardBolusId != null)
            EventData.ActionWizardConfirmed(wizardBolusId!!, correctionU).serialize()
        else actionKey
        if (key.isNotEmpty()) startService(IntentWearToMobile(this, key))
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

@Composable
private fun WizardConfirmPage(enabled: Boolean, totalInsulin: Double?, carbs: Int?, onConfirm: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val fmt = remember { DecimalFormat("0.00") }
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
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onConfirm()
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
        if (totalInsulin != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wizard_insulin_format, fmt.format(totalInsulin)),
                color = InsulinBlue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (carbs != null && carbs > 0) {
            Text(
                text = stringResource(R.string.wizard_carbs_short, carbs),
                color = CarbsOrange,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun WizardDetailPage(detail: EventData.WizardDetail, correctionSteps: Int, onCorrectionStepsChange: (Int) -> Unit) {
    val fmt2 = remember { DecimalFormat("0.00") }
    val fmt1 = remember { DecimalFormat("0.0") }
    val haptic = LocalHapticFeedback.current

    // correctionSteps == 0 → exactly 0.0 (no FP drift); otherwise multiply once for display
    val correctionU = if (correctionSteps == 0) 0.0 else correctionSteps * detail.bolusStep
    // Use unclampedInsulin as the base so that when IOB exceeds the calculated dose (raw < 0,
    // displayed as 0.00), the user must spend steps recovering to zero before going positive —
    // matching phone wizard behaviour.
    val adjustedTotal = (detail.unclampedInsulin + correctionU).coerceAtLeast(0.0)

    val totalIob = when {
        detail.includeBolusIOB && detail.includeBasalIOB -> detail.insulinFromBolusIOB + detail.insulinFromBasalIOB
        detail.includeBolusIOB                           -> detail.insulinFromBolusIOB
        detail.includeBasalIOB                           -> detail.insulinFromBasalIOB
        else                                             -> null
    }
    // New IOB = current IOB + adjusted bolus (projected total active insulin after delivery)
    val newIob = totalIob?.let { it + adjustedTotal }

    data class CalcRow(val label: String, val value: Double)

    val rows = buildList {
        if (detail.insulinFromBG != 0.0) {
            val ttLabel = detail.tempTargetLabel
            val label = if (!ttLabel.isNullOrEmpty())
                stringResource(R.string.wizard_result_bg_tt, ttLabel)
            else
                stringResource(R.string.wizard_result_bg)
            add(CalcRow(label, detail.insulinFromBG))
        }
        if (detail.insulinFromTrend != 0.0)
            add(CalcRow(stringResource(R.string.wizard_result_trend), detail.insulinFromTrend))
        if (detail.insulinFromCOB != 0.0)
            add(CalcRow(stringResource(R.string.wizard_result_cob, detail.cob), detail.insulinFromCOB))
        if (detail.carbs > 0)
            add(CalcRow(stringResource(R.string.wizard_result_carbs, detail.carbs), detail.insulinFromCarbs))
    }

    var isExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { if (isExpanded) it.verticalScroll(scrollState) else it }
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = if (isExpanded) 36.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Summary card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WearSummaryCardBg)
                .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.wizard_total_insulin),
                    color = WearSecondaryText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                // Integer step bounds derived from unclampedInsulin so limits are correct even
                // when the raw calculated dose is negative (IOB > calculated → total clamped to 0).
                // coerceAtLeast(0): when unclamped < 0 you can't decrease further (already at 0).
                // detail is stable across correctionSteps recompositions — remember to avoid recalculating on every tap.
                val maxDownSteps = remember(detail) { if (detail.bolusStep > 0.0) ((detail.unclampedInsulin / detail.bolusStep) + 0.01).toInt().coerceAtLeast(0) else 0 }
                val maxUpSteps   = remember(detail) { if (detail.bolusStep > 0.0 && detail.maxBolus > 0.0) (((detail.maxBolus - detail.unclampedInsulin) / detail.bolusStep) + 0.01).toInt() else Int.MAX_VALUE }
                // Mutable ref mirroring the hoisted correctionSteps so a held repeat loop (a single
                // long-running coroutine) always steps from the latest value instead of a stale
                // closure capture — same technique as PlusMinusInputScreen's step().
                val currentSteps = remember { mutableStateOf(correctionSteps) }
                SideEffect { currentSteps.value = correctionSteps }
                fun stepCorrection(delta: Int) {
                    val newValue = (currentSteps.value + delta).coerceIn(-maxDownSteps, maxUpSteps)
                    if (newValue != currentSteps.value) {
                        currentSteps.value = newValue
                        onCorrectionStepsChange(newValue)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val canDecrease  = detail.bolusStep > 0.0 && correctionSteps > -maxDownSteps
                    val canIncrease  = detail.bolusStep > 0.0 && correctionSteps < maxUpSteps
                    if (detail.bolusStep > 0.0) {
                        CorrectionStepButton(
                            isIncrement = false,
                            active = correctionSteps < 0,
                            enabled = canDecrease,
                            activeColor = WearInsulinNegative,
                            onStep = { stepCorrection(-1) },
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = fmt2.format(adjustedTotal),
                                color = InsulinBlue,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.insulin_unit_short),
                                color = InsulinBlue,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    if (detail.bolusStep > 0.0) {
                        Spacer(Modifier.width(6.dp))
                        CorrectionStepButton(
                            isIncrement = true,
                            active = correctionSteps > 0,
                            enabled = canIncrease,
                            activeColor = WearInsulinPositive,
                            onStep = { stepCorrection(1) },
                        )
                    }
                }
                val carbsFontSize = if (detail.eCarbsGrams > 0 || (correctionSteps != 0 && detail.carbTimeMinutes != 0)) 11.sp else 14.sp
                if (correctionSteps != 0 && detail.carbs == 0) {
                    val corrSign = if (correctionU > 0) "+" else ""
                    Text(
                        text = "$corrSign${fmt2.format(correctionU)} ${stringResource(R.string.insulin_unit_short)}",
                        color = InsulinBlue,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                if (detail.carbs > 0) {
                    val timeSign = if (detail.carbTimeMinutes > 0) "+" else ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = if (detail.carbTimeMinutes != 0)
                                stringResource(R.string.wizard_carbs_with_time, detail.carbs, "$timeSign${detail.carbTimeMinutes}")
                            else
                                stringResource(R.string.wizard_carbs_short, detail.carbs),
                            color = CarbsOrange,
                            fontSize = carbsFontSize,
                            fontWeight = FontWeight.Bold,
                        )
                        if (detail.alarm && detail.carbTimeMinutes != 0) {
                            Spacer(Modifier.width(3.dp))
                            Icon(
                                painter = painterResource(R.drawable.ic_alarm),
                                contentDescription = null,
                                tint = CarbsOrange,
                                modifier = Modifier.size(carbsFontSize.value.dp),
                            )
                        }
                        if (correctionSteps != 0) {
                            val corrSign = if (correctionU > 0) "+" else ""
                            Spacer(Modifier.width(3.dp))
                            Text(text = "(", color = WearSecondaryText, fontSize = carbsFontSize)
                            Text(
                                text = "$corrSign${fmt2.format(correctionU)} ${stringResource(R.string.insulin_unit_short)}",
                                color = InsulinBlue,
                                fontSize = carbsFontSize,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(text = ")", color = WearSecondaryText, fontSize = carbsFontSize)
                        }
                    }
                }
                if (detail.eCarbsGrams > 0) {
                    Text(
                        text = stringResource(R.string.wizard_result_ecarbs, detail.eCarbsGrams, detail.eCarbsDurationHours, detail.eCarbsDelayMinutes),
                        color = CarbsOrange,
                        fontSize = carbsFontSize,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // Calculation card (collapsible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(WearSummaryCardBg)
                .padding(5.dp),
        ) {
            Column {
                // Header — always visible, tappable to expand/collapse
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .padding(vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_calculation),
                            color = WearSecondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (isExpanded) "▲" else "▼",
                            color = WearSecondaryText,
                            fontSize = 12.sp,
                        )
                    }
                    if (newIob != null) {
                        Text(
                            text = stringResource(R.string.wizard_result_new_iob, fmt2.format(newIob)),
                            color = WearSecondaryText,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Expandable details
                AnimatedVisibility(visible = isExpanded) {
                    Column {
                        if (detail.ic > 0 && detail.sens > 0) {
                            Text(
                                text = stringResource(R.string.wizard_settings_format, fmt1.format(detail.ic), fmt1.format(detail.sens)),
                                color = WearSecondaryText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp),
                            )
                        }
                        WizardDetailDivider()
                        rows.forEach { row -> WizardDetailRow(row.label, row.value, fmt2) }

                        val subtotal = detail.insulinFromBG + detail.insulinFromTrend + detail.insulinFromCOB + detail.insulinFromCarbs
                        val showSubtotal = rows.size > 1
                        val showPercentage = detail.percentageCorrection != 100
                        if (showSubtotal || showPercentage) {
                            WizardDetailDivider()
                            if (showSubtotal) WizardDetailRow(stringResource(R.string.wizard_result_subtotal), subtotal, fmt2)
                            if (showPercentage) {
                                WizardDetailRow(
                                    stringResource(R.string.wizard_result_correction_percentage, detail.percentageCorrection),
                                    subtotal * detail.percentageCorrection / 100.0,
                                    fmt2,
                                )
                            }
                        }
                        val hasIob = totalIob != null && totalIob != 0.0
                        if (hasIob || correctionU != 0.0) {
                            WizardDetailDivider()
                            if (hasIob) WizardDetailRow(stringResource(R.string.wizard_result_iob), -totalIob!!, fmt2)
                            if (correctionU != 0.0) WizardDetailRow(stringResource(R.string.wizard_result_correction), correctionU, fmt2)
                        }
                        WizardDetailDivider()
                        WizardDetailRow(stringResource(R.string.wizard_result_total), adjustedTotal, fmt2)
                    }
                }
            }
        }
    }
}

@Composable
private fun WizardDetailRow(label: String, value: Double, fmt: DecimalFormat) {
    val sign = if (value >= 0) "+" else ""
    val valueColor = when {
        value > 0  -> WearInsulinPositive
        value < 0  -> WearInsulinNegative
        else       -> WearSecondaryText
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(if (value >= 0) WearInsulinPositive else WearInsulinNegative, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(text = label, color = WearSecondaryText, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(
            text = "$sign${fmt.format(value)} ${stringResource(R.string.insulin_unit_short)}",
            color = valueColor,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun WizardDetailDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(1.dp)
            .background(WearDivider),
    )
}

/**
 * Small ± correction button — press-and-hold repeats with acceleration, same timing as
 * [StepButton] in PlusMinusInputScreen.kt (100ms grace period so the pager can still claim a
 * swipe, then repeat every 300ms decaying down to a 40ms floor).
 */
@Composable
private fun CorrectionStepButton(
    isIncrement: Boolean,
    active: Boolean,
    enabled: Boolean,
    activeColor: Color,
    onStep: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(when {
                active  -> activeColor
                enabled -> InsulinBlue.copy(alpha = 0.25f)
                else    -> Color(0xFF303030)
            })
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onPress = {
                        var stepped = false
                        coroutineScope {
                            val job = launch {
                                delay(100)          // allow pager to claim swipe gestures first
                                stepped = true
                                onStep()
                                var repeatDelay = 300L
                                while (true) {
                                    delay(repeatDelay)
                                    onStep()
                                    repeatDelay = maxOf(40L, (repeatDelay * 0.75).toLong())
                                }
                            }
                            val released = tryAwaitRelease()  // false if pager cancelled the gesture
                            job.cancel()
                            if (!stepped && released) onStep()  // quick tap under 100ms threshold
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isIncrement) "+" else "−",
            color = if (enabled) Color.White else Color(0xFF606060),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
