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
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import app.aaps.core.interfaces.di.injectMetroMembers
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.collectResilient
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.ActiveSceneInfo
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.core.interfaces.rx.weardata.OapsResultInfo
import app.aaps.core.interfaces.rx.weardata.ProfileInfo
import app.aaps.core.interfaces.rx.weardata.TargetRange
import app.aaps.core.interfaces.rx.weardata.TempTargetInfo
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.R
import app.aaps.wear.interaction.actions.InsulinBlue
import app.aaps.wear.interaction.actions.LoopClosedColor
import app.aaps.wear.interaction.actions.LoopDisabledColor
import app.aaps.wear.interaction.actions.LoopUnknownColor
import app.aaps.wear.interaction.actions.ScenePurple
import app.aaps.wear.interaction.actions.TempTargetYellow
import app.aaps.wear.interaction.actions.WearDivider
import app.aaps.wear.interaction.actions.WearSecondaryText
import app.aaps.wear.interaction.actions.WearSummaryCardBg
import app.aaps.wear.interaction.actions.formatDurationMinutes
import app.aaps.wear.interaction.actions.toTextColor
import dev.zacsweers.metro.Inject
import java.util.Date
import kotlin.math.abs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart

// Loop mode / insulin / secondary-text colors shared with the wizard result screen live in
// app.aaps.wear.interaction.actions.PlusMinusInputScreen.kt — imported above so the two screens
// can't drift apart. Only colors unique to this screen are declared here.
private val TempBasalColor         = Color(0xFFFF9800)
private val TargetsAccentColor     = Color(0xFF1E88E5)
private val TempTargetBg           = Color(0x1AF4D700)
private val SceneBg                = Color(0x1ACE93D8)
// Profile: teal, deliberately far from the scene purple above it - two purples side by side on a
// small screen read as the same thing
private val ProfileAccentColor     = Color(0xFF26A69A)
private val ProfileBg              = Color(0x1A26A69A)
private val AutosensTargetBg       = Color(0x1A77DD77)

private fun loopAgeColor(ageMs: Long): Color {
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

    private var uiState by mutableStateOf<LoopStatusUiState>(LoopStatusUiState.Loading)

    override fun onCreate(savedInstanceState: Bundle?) {
        injectMetroMembers(this)
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

        // lifecycleScope is Main and dies with the activity, so runOnUiThread is no longer needed.
        // The Rx onError put the screen into an error state rather than only logging, so that is kept
        // explicitly - collectResilient on its own would log and carry on with the UI still spinning.
        rxBus.toFlow(EventData.LoopStatusResponse::class)
            .collectResilient(lifecycleScope, aapsLogger, LTag.WEAR, start = CoroutineStart.UNDISPATCHED) { event ->
                try {
                    aapsLogger.debug(LTag.WEAR, "Received loop status response")
                    uiState = LoopStatusUiState.Success(event.data)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    aapsLogger.error(LTag.WEAR, "Error receiving loop status", e)
                    uiState = LoopStatusUiState.Error(getString(R.string.loop_status_error))
                }
            }
    }

    override fun onResume() {
        super.onResume()
        requestLoopStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        HeaderCard(mode = data.loopMode, apsName = data.apsName, modeEndTime = data.modeEndTime)
        ResultCard(
            lastRun = data.lastRun,
            lastEnact = data.lastEnact,
            oapsResult = data.oapsResult,
            dateUtil = dateUtil
        )
        data.activeScene?.let { SceneCard(scene = it) }
        TargetsCard(tempTarget = data.tempTarget, autosensTarget = data.autosensTarget, defaultRange = data.defaultRange, dateUtil = dateUtil)
        ProfileCard(profile = data.profile)
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
            .background(WearSummaryCardBg)
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
        Text(text = label, color = WearSecondaryText, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(text = value, color = valueColor, fontSize = 12.sp)
    }
}

/**
 * The "1h 15' (14:30)" line under a temporary target or profile switch, with the scene icon at its
 * end when the active scene set it. The scene card says what the scene is; this says which of the
 * things below it are the scene's doing.
 */
@Composable
private fun DurationLine(text: String, fromScene: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, color = WearSecondaryText, fontSize = 11.sp, modifier = Modifier.weight(1f))
        if (fromScene) {
            Image(
                painter = painterResource(R.drawable.ic_scene_purple),
                contentDescription = stringResource(R.string.loop_status_set_by_scene),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WearDivider)
    )
}

// ─── Header Card ──────────────────────────────────────────────────────────────

@Composable
private fun HeaderCard(mode: LoopStatusData.LoopMode, apsName: String?, modeEndTime: Long?) {
    val context = LocalContext.current

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
            color = mode.toTextColor(),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        // Remaining duration of a temporary mode (suspend/disconnect/superbolus); hidden once expired
        val remainingMinutes = modeEndTime?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() } ?: 0
        if (modeEndTime != null && remainingMinutes > 0) {
            val endTimeStr = remember(modeEndTime) {
                DateFormat.getTimeFormat(context).format(Date(modeEndTime))
            }
            Text(
                text = stringResource(R.string.loop_status_duration_until, formatDurationMinutes(remainingMinutes), endTimeStr),
                color = WearSecondaryText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
        if (apsName != null) {
            Text(
                text = apsName,
                color = WearSecondaryText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp)
            )
        }
    }
}

// ─── Scene Card ───────────────────────────────────────────────────────────────

/**
 * The active scene: its name, how long it still runs, and the follow-up that starts when it ends.
 * After the loop result, so the header and the result stay together at the top, and the scene
 * comes with the things it changes: the targets and the profile below it.
 */
@Composable
private fun SceneCard(scene: ActiveSceneInfo) {
    val context = LocalContext.current

    StatusCard {
        CardTitle(stringResource(R.string.label_scene_tile), ScenePurple)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .background(SceneBg)
                .padding(8.dp)
        ) {
            Column {
                Text(
                    text = scene.name,
                    color = ScenePurple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                // A local, because a property from another module cannot be smart-cast
                val endTime = scene.endTime
                val remainingMinutes = endTime?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() } ?: 0
                val timeLine = when {
                    endTime == null      -> stringResource(R.string.loop_status_scene_until_ended)
                    remainingMinutes > 0 -> {
                        val endTimeStr = remember(endTime) { DateFormat.getTimeFormat(context).format(Date(endTime)) }
                        stringResource(R.string.loop_status_duration_until, formatDurationMinutes(remainingMinutes), endTimeStr)
                    }
                    // Expired, banner still up on the phone: it can still be ended from the tile
                    else                 -> stringResource(R.string.loop_status_scene_ended)
                }
                Text(
                    text = timeLine,
                    color = WearSecondaryText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
                scene.chainTargetName?.let {
                    Spacer(Modifier.height(4.dp))
                    InfoRow(label = stringResource(R.string.loop_status_scene_follow_up), value = it, valueColor = ScenePurple)
                }
            }
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
                    valueColor = loopAgeColor(enactAgeMs)
                )
            } else {
                InfoRow(
                    label = stringResource(R.string.loop_status_last_run),
                    value = runTimeStr,
                    valueColor = loopAgeColor(runAgeMs)
                )
                Spacer(Modifier.height(4.dp))
                InfoRow(
                    label = stringResource(R.string.loop_status_last_enact),
                    value = enactTimeStr,
                    valueColor = loopAgeColor(enactAgeMs)
                )
            }
        } else {
            InfoRow(
                label = stringResource(R.string.loop_status_last_run),
                value = runTimeStr,
                valueColor = loopAgeColor(runAgeMs)
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
            valueColor = InsulinBlue
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
                        value = stringResource(R.string.loop_status_duration_remaining, formatDurationMinutes(duration))
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
                    value = formatDurationMinutes(duration)
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
                color = WearSecondaryText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (isReasonExpanded) "▲" else "▼",
                color = WearSecondaryText,
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
    autosensTarget: String?,
    defaultRange: TargetRange,
    dateUtil: DateUtil
) {
    val context = LocalContext.current

    StatusCard {
        CardTitle(stringResource(R.string.loop_status_targets), TargetsAccentColor)
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
                            color = TempTargetYellow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${tempTarget.targetDisplay} ${tempTarget.units}",
                            color = TempTargetYellow,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    DurationLine(
                        text = stringResource(R.string.loop_status_duration_until, formatDurationMinutes(tempTarget.durationMinutes), endTimeStr),
                        fromScene = tempTarget.fromScene
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (autosensTarget != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(AutosensTargetBg)
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.loop_status_adjusted_target),
                        color = AutosensTargetColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = autosensTarget,
                        color = AutosensTargetColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
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
}

// ─── Profile Card ─────────────────────────────────────────────────────────────

/**
 * The profile in force. A permanent switch is the name and, only when they differ from the
 * defaults, its percentage and timeshift. A temporary switch gets the tinted box the temp target
 * uses: name, percentage, timeshift, how long it still runs, and the profile that returns when it
 * ends - the same rule as the phone's profile management, so the two never disagree.
 */
@Composable
private fun ProfileCard(profile: ProfileInfo?) {
    val context = LocalContext.current

    StatusCard {
        CardTitle(stringResource(R.string.loop_status_profile), ProfileAccentColor)
        Spacer(Modifier.height(8.dp))

        if (profile == null) {
            Text(text = stringResource(R.string.loop_status_no_profile), color = WearSecondaryText, fontSize = 12.sp)
            return@StatusCard
        }

        val endTime = profile.endTime
        if (endTime != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(ProfileBg)
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = profile.name,
                        color = ProfileAccentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    ProfileModifierRows(profile)
                    // Remaining time; once past the end the phone has already put the next switch in force
                    val remainingMinutes = ((endTime - System.currentTimeMillis()) / 60_000).toInt()
                    if (remainingMinutes > 0) {
                        val endTimeStr = remember(endTime) { DateFormat.getTimeFormat(context).format(Date(endTime)) }
                        DurationLine(
                            text = stringResource(R.string.loop_status_duration_until, formatDurationMinutes(remainingMinutes), endTimeStr),
                            fromScene = profile.fromScene
                        )
                    }
                    profile.returnsTo?.let {
                        Spacer(Modifier.height(4.dp))
                        InfoRow(label = stringResource(R.string.loop_status_profile_returns_to), value = it, valueColor = ProfileAccentColor)
                    }
                }
            }
        } else {
            Text(
                text = profile.name,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            ProfileModifierRows(profile)
        }
    }
}

/** Percentage and timeshift rows, each only when it changes something */
@Composable
private fun ProfileModifierRows(profile: ProfileInfo) {
    if (profile.percentage != 100) {
        InfoRow(
            label = stringResource(R.string.loop_status_profile_percentage),
            value = stringResource(R.string.loop_status_profile_percentage_value, profile.percentage)
        )
    }
    if (profile.timeshiftHours != 0) {
        InfoRow(
            label = stringResource(R.string.loop_status_profile_timeshift),
            value = stringResource(R.string.loop_status_profile_timeshift_value, profile.timeshiftHours)
        )
    }
}
