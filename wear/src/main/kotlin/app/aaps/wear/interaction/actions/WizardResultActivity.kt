package app.aaps.wear.interaction.actions

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import dagger.android.support.DaggerAppCompatActivity
import java.text.DecimalFormat
import javax.inject.Inject

data class WizardCalculationRow(
    val label: String,
    val value: Double,
    @StringRes val unitResId: Int = R.string.insulin_unit_short
)

// Colors from wear/res/values/colors.xml
private val InsulinPositive = Color(0xFF66BB6A)
private val InsulinNegative = Color(0xFFEF5350)
private val SecondaryText   = Color(0xFFB0BEC5)
private val Divider         = Color(0xFF37474F)
private val SummaryCardBg   = Color(0xFF0D47A1)
private val CalcCardBg      = Color(0xFF263238)

class WizardResultActivity : DaggerAppCompatActivity() {

    @Inject lateinit var rxBus: RxBus

    // Data parsed from intent
    private var timestamp: Long = 0
    private var totalInsulin: Double = 0.0
    private var carbs: Int = 0
    private var ic: Double = 0.0
    private var sens: Double = 0.0
    private var insulinCarbs: Double = 0.0
    private var insulinBg: Double? = null
    private var insulinCob: Double? = null
    private var insulinBolusIob: Double? = null
    private var insulinBasalIob: Double? = null
    private var insulinTrend: Double? = null
    private var tempTarget: String? = null
    private var percentage: Int = 100
    private var cob: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vibrateOnResult()

        timestamp            = intent.getLongExtra("timestamp", 0L)
        totalInsulin         = intent.getDoubleExtra("total_insulin", 0.0)
        carbs                = intent.getIntExtra("carbs", 0)
        ic                   = intent.getDoubleExtra("ic", 0.0)
        sens                 = intent.getDoubleExtra("sens", 0.0)
        insulinCarbs         = intent.getDoubleExtra("insulin_carbs", 0.0)
        insulinBg            = intent.getDoubleExtra("insulin_bg", Double.NaN).takeUnless { it.isNaN() }
        insulinCob           = intent.getDoubleExtra("insulin_cob", Double.NaN).takeUnless { it.isNaN() }
        insulinBolusIob      = intent.getDoubleExtra("insulin_bolus_iob", Double.NaN).takeUnless { it.isNaN() }
        insulinBasalIob      = intent.getDoubleExtra("insulin_basal_iob", Double.NaN).takeUnless { it.isNaN() }
        insulinTrend         = intent.getDoubleExtra("insulin_trend", Double.NaN).takeUnless { it.isNaN() }
        tempTarget           = intent.getStringExtra("temp_target")
        percentage           = intent.getIntExtra("percentage", 100)
        cob                  = intent.getDoubleExtra("cob", 0.0)

        setContent {
            MaterialTheme {
                val pagerState = rememberPagerState(pageCount = { 2 })
                Box(modifier = Modifier.fillMaxSize()) {
                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> WizardResultScreen(
                                totalInsulin          = totalInsulin,
                                carbs                 = carbs,
                                ic                    = ic,
                                sens                  = sens,
                                insulinCarbs          = insulinCarbs,
                                insulinBg             = insulinBg,
                                insulinCob            = insulinCob,
                                insulinBolusIob       = insulinBolusIob,
                                insulinBasalIob       = insulinBasalIob,
                                insulinTrend          = insulinTrend,
                                tempTarget            = tempTarget,
                                percentage            = percentage,
                                cob                   = cob
                            )
                            1 -> WizardConfirmScreen(
                                totalInsulin = totalInsulin,
                                carbs        = carbs,
                                onConfirm    = ::confirmAndFinish
                            )
                        }
                    }
                    HorizontalPageIndicator(
                        pagerState = pagerState,
                        modifier   = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                    )
                }
            }
        }
    }

    private fun confirmAndFinish() {
        rxBus.send(EventWearToMobile(EventData.ActionWizardConfirmed(timestamp)))
        val intent = Intent(this, ConfirmationActivity::class.java).apply {
            putExtra(ConfirmationActivity.EXTRA_ANIMATION_TYPE, ConfirmationActivity.SUCCESS_ANIMATION)
            putExtra(ConfirmationActivity.EXTRA_MESSAGE, getString(R.string.wizard_confirmation_sent))
        }
        startActivity(intent)
        finish()
    }

    @Suppress("DEPRECATION")
    private fun vibrateOnResult() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val timings    = longArrayOf(0, 100, 50, 100)
                    val amplitudes = intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, VibrationEffect.DEFAULT_AMPLITUDE)
                    it.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    it.vibrate(longArrayOf(0, 100, 50, 100), -1)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WizardResultActivity", "Vibration error", e)
        }
    }
}

// ─── Wizard Result Screen ─────────────────────────────────────────────────────

@Composable
private fun WizardResultScreen(
    totalInsulin: Double,
    carbs: Int,
    ic: Double,
    sens: Double,
    insulinCarbs: Double,
    insulinBg: Double?,
    insulinCob: Double?,
    insulinBolusIob: Double?,
    insulinBasalIob: Double?,
    insulinTrend: Double?,
    tempTarget: String?,
    percentage: Int,
    cob: Double
) {
    val fmt2 = remember { DecimalFormat("0.00") }
    val fmt1 = remember { DecimalFormat("0.0") }

    val totalIob = when {
        insulinBolusIob != null && insulinBasalIob != null -> insulinBolusIob + insulinBasalIob
        insulinBolusIob != null -> insulinBolusIob
        insulinBasalIob != null -> insulinBasalIob
        else -> null
    }
    val newIob = totalIob?.let { totalInsulin - it }

    val rows = buildList {
        if (insulinBg != null) {
            val label = if (!tempTarget.isNullOrEmpty())
                stringResource(R.string.wizard_result_bg_tt, tempTarget)
            else
                stringResource(R.string.wizard_result_bg)
            add(WizardCalculationRow(label, insulinBg))
        }
        if (insulinTrend != null && insulinTrend != 0.0) {
            add(WizardCalculationRow(stringResource(R.string.wizard_result_trend), insulinTrend))
        }
        if (insulinCob != null && insulinCob != 0.0) {
            add(WizardCalculationRow(stringResource(R.string.wizard_result_cob, cob), insulinCob))
        }
        if (carbs > 0) {
            add(WizardCalculationRow(stringResource(R.string.wizard_result_carbs, carbs), insulinCarbs))
        }
    }

    var isCalcExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .let { if (isCalcExpanded) it.verticalScroll(scrollState) else it }
            .padding(horizontal = 10.dp)
            .padding(top = 10.dp, bottom = if (isCalcExpanded) 36.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Summary card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SummaryCardBg)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.wizard_total_insulin),
                    color = SecondaryText,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = fmt2.format(totalInsulin),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.units_short),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stringResource(R.string.wizard_carbs_format, carbs),
                    color = CarbsOrange,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Calculation details card (collapsible)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(CalcCardBg)
                .padding(5.dp)
        ) {
            Column {
                // Header — always visible, tappable
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isCalcExpanded = !isCalcExpanded }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.wizard_calculation),
                            color = SecondaryText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isCalcExpanded) "▲" else "▼",
                            color = SecondaryText,
                            fontSize = 12.sp
                        )
                    }
                    if (newIob != null) {
                        Text(
                            text = stringResource(R.string.wizard_result_new_iob, fmt2.format(newIob)),
                            color = Color(0xFF90A4AE),
                            fontSize = 11.sp
                        )
                    }
                }

                // Expandable details
                AnimatedVisibility(visible = isCalcExpanded) {
                    Column {
                        if (ic > 0 && sens > 0) {
                            Text(
                                text = stringResource(R.string.wizard_settings_format, fmt1.format(ic), fmt1.format(sens)),
                                color = SecondaryText,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        HorizontalDivider()

                        rows.forEach { row -> CalculationRow(row = row) }

                        val subtotalValue = (insulinBg ?: 0.0) + (insulinTrend ?: 0.0) + (insulinCob ?: 0.0) + insulinCarbs
                        val showSubtotal = rows.size > 1
                        val showPercentage = percentage != 100
                        if (showSubtotal || showPercentage) {
                            HorizontalDivider()
                            if (showSubtotal) {
                                CalculationRow(
                                    row = WizardCalculationRow(
                                        label = stringResource(R.string.wizard_result_subtotal),
                                        value = subtotalValue
                                    )
                                )
                            }
                            if (showPercentage) {
                                val afterPercentage = subtotalValue * percentage / 100.0
                                CalculationRow(
                                    row = WizardCalculationRow(
                                        label = stringResource(R.string.wizard_result_correction_percentage, percentage),
                                        value = afterPercentage
                                    )
                                )
                            }
                        }

                        if (totalIob != null && totalIob != 0.0) {
                            HorizontalDivider()
                            CalculationRow(
                                row = WizardCalculationRow(
                                    label = stringResource(R.string.wizard_result_iob),
                                    value = totalIob
                                )
                            )
                        }
                        HorizontalDivider()
                        CalculationRow(
                            row = WizardCalculationRow(
                                label = stringResource(R.string.wizard_result_total),
                                value = totalInsulin
                            )
                        )
                    }
                }
            }
        }

    }
}

@Composable
private fun CalculationRow(row: WizardCalculationRow) {
    val fmt = remember { DecimalFormat("0.00") }
    val unit = stringResource(row.unitResId)
    val valueStr = if (row.value >= 0) "+${fmt.format(row.value)}" else fmt.format(row.value)
    val valueColor = when {
        row.value > 0 -> InsulinPositive
        row.value < 0 -> InsulinNegative
        else          -> SecondaryText
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (row.value >= 0) InsulinPositive else InsulinNegative,
                    shape = CircleShape
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(text = row.label, color = SecondaryText, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(text = "$valueStr $unit", color = valueColor, fontSize = 11.sp)
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(1.dp)
            .background(Divider)
    )
}

// ─── Wizard Confirm Screen ────────────────────────────────────────────────────

@Composable
private fun WizardConfirmScreen(
    totalInsulin: Double,
    carbs: Int,
    onConfirm: () -> Unit
) {
    val fmt = remember { DecimalFormat("0.00") }
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
            text = stringResource(R.string.confirm),
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(14.dp))
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
            text = stringResource(R.string.wizard_insulin_format, fmt.format(totalInsulin)),
            color = InsulinBlue,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.wizard_carbs_format, carbs),
            color = CarbsOrange,
            fontSize = 12.sp
        )
    }
}
