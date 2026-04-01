package app.aaps.wear.interaction.activities

import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.core.interfaces.rx.weardata.OapsResultInfo
import app.aaps.core.interfaces.rx.weardata.TargetRange
import app.aaps.core.interfaces.rx.weardata.TempTargetInfo
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.R
import dagger.android.AndroidInjection
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.Date
import javax.inject.Inject
import kotlin.math.abs

// Colors sourced from wear/res/values/colors.xml
private val LoopClosedColor       = Color(0xFF00C03E)
private val LoopOpenColor         = Color(0xFF4983D7)
private val LoopLgsColor          = Color(0xFF800080)
private val LoopSuspendedColor    = Color(0xFFFFFF13)
private val LoopDisabledColor     = Color(0xFFFF1313)
private val LoopDisconnectedColor = Color(0xFF939393)
private val LoopUnknownColor      = Color(0xFF9E9E9E)
private val LoopSuperbolusColor   = Color(0xFFFFAE01)
private val TempBasalColor        = Color(0xFFFF9800)
private val IobColor              = Color(0xFF1E88E5)
private val BolusColor            = Color(0xFF1EA3E5)
private val TempTargetActiveColor = Color(0xFFF4D700)
private val TempTargetBg          = Color(0x1AF4D700)
private val White70               = Color(0xB3FFFFFF)
private val White20               = Color(0x33FFFFFF)
private val CardBg                = Color(0xFF1A1A1A)

private fun LoopStatusData.LoopMode.toColor(): Color = when (this) {
    LoopStatusData.LoopMode.CLOSED       -> LoopClosedColor
    LoopStatusData.LoopMode.OPEN         -> LoopOpenColor
    LoopStatusData.LoopMode.LGS          -> LoopLgsColor
    LoopStatusData.LoopMode.DISABLED     -> LoopDisabledColor
    LoopStatusData.LoopMode.SUSPENDED       -> LoopSuspendedColor
    LoopStatusData.LoopMode.PUMP_SUSPENDED  -> LoopDisabledColor
    LoopStatusData.LoopMode.DST_SUSPENDED   -> LoopDisabledColor
    LoopStatusData.LoopMode.DISCONNECTED    -> LoopDisconnectedColor
    LoopStatusData.LoopMode.SUPERBOLUS   -> LoopSuperbolusColor
    LoopStatusData.LoopMode.UNKNOWN      -> LoopUnknownColor
}

private fun ageColor(ageMs: Long): Color {
    val minutes = ageMs / 60_000
    return when {
        minutes < 4  -> LoopClosedColor
        minutes < 10 -> TempBasalColor
        else         -> LoopDisabledColor
    }
}

sealed class LoopStatusUiState {
    object Loading : LoopStatusUiState()
    data class Error(val message: String) : LoopStatusUiState()
    data class Success(val data: LoopStatusData) : LoopStatusUiState()
}

class LoopStatusActivity : AppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()
    private var uiState by mutableStateOf<LoopStatusUiState>(LoopStatusUiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                LoopStatusScreen(
                    uiState = uiState,
                    dateUtil = dateUtil,
                    onRefresh = ::requestLoopStatus
                )
            }
        }

        disposable += rxBus
            .toObservable(EventData.LoopStatusResponse::class.java)
            .subscribe({ event ->
                aapsLogger.debug(LTag.WEAR, "Received loop status response")
                runOnUiThread { uiState = LoopStatusUiState.Success(event.data) }
            }, { error ->
                aapsLogger.error(LTag.WEAR, "Error receiving loop status", error)
                runOnUiThread { uiState = LoopStatusUiState.Error(getString(R.string.loop_status_error)) }
            })
    }

    override fun onResume() {
        super.onResume()
        requestLoopStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun requestLoopStatus() {
        if (uiState !is LoopStatusUiState.Success) uiState = LoopStatusUiState.Loading
        aapsLogger.debug(LTag.WEAR, "Requesting detailed loop status")
        rxBus.send(EventWearToMobile(EventData.ActionLoopStatusDetailed(System.currentTimeMillis())))
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
private fun LoopStatusScreen(
    uiState: LoopStatusUiState,
    dateUtil: DateUtil,
    onRefresh: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (uiState) {
            is LoopStatusUiState.Loading ->
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))

            is LoopStatusUiState.Error ->
                Text(
                    text = uiState.message,
                    color = LoopDisabledColor,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(12.dp)
                )

            is LoopStatusUiState.Success ->
                LoopStatusContent(data = uiState.data, dateUtil = dateUtil, onRefresh = onRefresh)
        }
    }
}

@Composable
private fun LoopStatusContent(
    data: LoopStatusData,
    dateUtil: DateUtil,
    onRefresh: () -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HeaderCard(mode = data.loopMode, apsName = data.apsName)
        ResultCard(
            lastRun = data.lastRun,
            lastEnact = data.lastEnact,
            oapsResult = data.oapsResult,
            dateUtil = dateUtil
        )
        TargetsCard(tempTarget = data.tempTarget, defaultRange = data.defaultRange, dateUtil = dateUtil)
        RefreshButton(onClick = onRefresh)
    }
}

// ─── Refresh button ───────────────────────────────────────────────────────────

@Composable
private fun RefreshButton(onClick: () -> Unit) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.93f else 1f, animationSpec = tween(100), label = "refresh_scale")
    val alpha by animateFloatAsState(targetValue = if (isPressed) 0.6f else 1f, animationSpec = tween(100), label = "refresh_alpha")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .alpha(alpha)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A237E))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val released = tryAwaitRelease()
                        isPressed = false
                        if (released) onClick()
                    }
                )
            }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.loop_status_refresh),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.wrapContentSize()
        )
    }
}

// ─── Shared primitives ────────────────────────────────────────────────────────

@Composable
private fun StatusCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBg)
            .padding(10.dp)
    ) {
        Column { content() }
    }
}

@Composable
private fun CardTitle(title: String, accentColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(accentColor)
        )
        Text(
            text = title,
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = Color.White) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = White70, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = valueColor, fontSize = 12.sp)
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(White20)
    )
}

// ─── Header Card ──────────────────────────────────────────────────────────────

@Composable
private fun HeaderCard(mode: LoopStatusData.LoopMode, apsName: String?) {
    StatusCard {
        Text(
            text = when (mode) {
                LoopStatusData.LoopMode.CLOSED       -> stringResource(R.string.loop_status_closed).uppercase()
                LoopStatusData.LoopMode.OPEN         -> stringResource(R.string.loop_status_open).uppercase()
                LoopStatusData.LoopMode.LGS          -> stringResource(R.string.loop_status_lgs).uppercase()
                LoopStatusData.LoopMode.DISABLED     -> stringResource(R.string.loop_status_disabled).uppercase()
                LoopStatusData.LoopMode.SUSPENDED      -> stringResource(R.string.loop_status_suspended).uppercase()
                LoopStatusData.LoopMode.PUMP_SUSPENDED -> stringResource(R.string.loop_status_pump_suspended).uppercase()
                LoopStatusData.LoopMode.DST_SUSPENDED  -> stringResource(R.string.loop_status_dst_suspended).uppercase()
                LoopStatusData.LoopMode.DISCONNECTED   -> stringResource(R.string.loop_status_disconnected).uppercase()
                LoopStatusData.LoopMode.SUPERBOLUS   -> stringResource(R.string.loop_status_superbolus).uppercase()
                LoopStatusData.LoopMode.UNKNOWN      -> stringResource(R.string.loop_status_unknown).uppercase()
            },
            color = mode.toColor(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        if (apsName != null) {
            Text(
                text = apsName,
                color = White70,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}

// ─── Result Card ──────────────────────────────────────────────────────────────

@Composable
private fun ResultCard(
    lastRun: Long?,
    lastEnact: Long?,
    oapsResult: OapsResultInfo?,
    dateUtil: DateUtil
) {
    var isReasonExpanded by remember { mutableStateOf(false) }

    StatusCard {
        CardTitle(stringResource(R.string.loop_status_result), TempBasalColor)
        Spacer(Modifier.height(8.dp))
        LastRunSection(lastRun = lastRun, lastEnact = lastEnact, dateUtil = dateUtil)
        if (oapsResult != null) {
            Spacer(Modifier.height(8.dp))
            RowDivider()
            Spacer(Modifier.height(8.dp))
            OapsResultSection(
                result = oapsResult,
                isReasonExpanded = isReasonExpanded,
                onToggleReason = { isReasonExpanded = !isReasonExpanded }
            )
        }
    }
}

@Composable
private fun LastRunSection(lastRun: Long?, lastEnact: Long?, dateUtil: DateUtil) {
    val now = System.currentTimeMillis()

    if (lastRun != null) {
        val runTimeStr = dateUtil.timeString(lastRun)
        val runAgeMs   = now - lastRun

        if (lastEnact != null) {
            val enactTimeStr  = dateUtil.timeString(lastEnact)
            val enactAgeMs    = now - lastEnact
            val timesAreClose = abs(lastRun - lastEnact) <= 30_000L

            if (timesAreClose) {
                InfoRow(
                    label = stringResource(R.string.loop_status_last_run_enact),
                    value = enactTimeStr,
                    valueColor = ageColor(enactAgeMs)
                )
            } else {
                InfoRow(
                    label = stringResource(R.string.loop_status_last_run),
                    value = runTimeStr,
                    valueColor = ageColor(runAgeMs)
                )
                Spacer(Modifier.height(4.dp))
                InfoRow(
                    label = stringResource(R.string.loop_status_last_enact),
                    value = enactTimeStr,
                    valueColor = ageColor(enactAgeMs)
                )
            }
        } else {
            InfoRow(
                label = stringResource(R.string.loop_status_last_run),
                value = runTimeStr,
                valueColor = ageColor(runAgeMs)
            )
        }
    } else {
        InfoRow(
            label = stringResource(R.string.loop_status_last_run),
            value = stringResource(R.string.loop_status_no_last_run),
            valueColor = LoopUnknownColor
        )
    }
}

@Composable
private fun OapsResultSection(
    result: OapsResultInfo,
    isReasonExpanded: Boolean,
    onToggleReason: () -> Unit
) {
    // Local vals required: Kotlin cannot smart-cast public API properties from other modules
    val rate        = result.rate
    val ratePercent = result.ratePercent ?: 0
    val duration    = result.duration

    val smb = result.smbAmount
    if (smb != null && smb > 0.0) {
        InfoRow(
            label = stringResource(R.string.loop_status_smb_text),
            value = stringResource(R.string.loop_status_smb, smb),
            valueColor = BolusColor
        )
        Spacer(Modifier.height(2.dp))
    }

    when {
        result.isLetTempRun -> {
            Text(
                text = stringResource(R.string.loop_status_tbr_continues),
                color = LoopClosedColor,
                fontSize = 12.sp
            )
            if (rate != null) {
                Spacer(Modifier.height(2.dp))
                InfoRow(
                    label = stringResource(R.string.loop_status_basal_rate),
                    value = stringResource(R.string.loop_status_tbr_rate, rate, ratePercent)
                )
                if (duration != null) {
                    Spacer(Modifier.height(4.dp))
                    InfoRow(
                        label = stringResource(R.string.loop_status_duration),
                        value = stringResource(R.string.loop_status_tbr_duration_remaining, duration)
                    )
                }
            }
        }

        ratePercent == 100 -> {
            Text(
                text = stringResource(R.string.loop_status_tbr_cancel),
                color = LoopClosedColor,
                fontSize = 12.sp
            )
            if (rate != null) {
                Spacer(Modifier.height(2.dp))
                InfoRow(
                    label = stringResource(R.string.loop_status_basal_rate),
                    value = stringResource(R.string.loop_status_tbr_rate, rate, ratePercent)
                )
            }
        }

        else -> {
            if (rate != null) {
                InfoRow(
                    label = stringResource(R.string.loop_status_basal_rate),
                    value = stringResource(R.string.loop_status_tbr_rate, rate, ratePercent)
                )
            }
            if (duration != null) {
                Spacer(Modifier.height(4.dp))
                InfoRow(
                    label = stringResource(R.string.loop_status_duration),
                    value = stringResource(R.string.loop_status_tbr_duration, duration)
                )
            }
        }
    }

    if (result.reason.isNotEmpty()) {
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleReason)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.loop_status_oaps_reason),
                color = White70,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isReasonExpanded) "▲" else "▼",
                color = White70,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        AnimatedVisibility(visible = isReasonExpanded) {
            Text(
                text = result.reason,
                color = Color.White,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

// ─── Targets Card ─────────────────────────────────────────────────────────────

@Composable
private fun TargetsCard(
    tempTarget: TempTargetInfo?,
    defaultRange: TargetRange,
    dateUtil: DateUtil
) {
    val context = LocalContext.current

    StatusCard {
        CardTitle(stringResource(R.string.loop_status_targets), IobColor)
        Spacer(Modifier.height(8.dp))

        if (tempTarget != null) {
            val endTimeStr = remember(tempTarget.endTime) {
                DateFormat.getTimeFormat(context).format(Date(tempTarget.endTime))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(TempTargetBg)
                    .padding(8.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.loop_status_temp_target),
                            color = TempTargetActiveColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${tempTarget.targetDisplay} ${tempTarget.units}",
                            color = TempTargetActiveColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = stringResource(R.string.loop_status_tempt_duration, tempTarget.durationMinutes, endTimeStr),
                        color = White70,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (defaultRange.lowDisplay != defaultRange.highDisplay) {
            InfoRow(
                label = stringResource(R.string.loop_status_target_range),
                value = "${defaultRange.lowDisplay} - ${defaultRange.highDisplay} ${defaultRange.units}"
            )
            Spacer(Modifier.height(4.dp))
        }
        InfoRow(
            label = stringResource(R.string.loop_status_target),
            value = "${defaultRange.targetDisplay} ${defaultRange.units}"
        )
    }
}
