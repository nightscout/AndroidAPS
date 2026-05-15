package app.aaps.wear.interaction.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.AnchorType
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.curvedText
import app.aaps.wear.R
import app.aaps.wear.data.ComplicationData
import app.aaps.wear.data.ComplicationDataRepository
import app.aaps.wear.interaction.menus.MainMenuActivity
import app.aaps.wear.interaction.utils.DisplayFormat
import dagger.android.AndroidInjection
import javax.inject.Inject

class BgGraphActivity : AppCompatActivity() {

    @Inject lateinit var complicationDataRepository: ComplicationDataRepository
    @Inject lateinit var displayFormat: DisplayFormat

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                BgGraphScreen(
                    repository = complicationDataRepository,
                    displayFormat = displayFormat
                )
            }
        }
    }
}

@Composable
private fun BgGraphScreen(repository: ComplicationDataRepository, displayFormat: DisplayFormat) {
    val data by repository.complicationData.collectAsState(initial = ComplicationData())
    var windowIdx by remember { mutableIntStateOf(0) }
    val historyHours = historyHoursCycle[windowIdx]

    val bgData = data.bgData
    val statusData = data.statusData
    val now = System.currentTimeMillis()
    val ageMs = now - bgData.timeStamp
    val ageMin = ageMs / 60_000
    val hourUnit = stringResource(R.string.hour_short)
    val minUnit = stringResource(R.string.minute_short)

    val isLoading = bgData.timeStamp == 0L

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        CurvedLayout(anchor = 270f, anchorType = AnchorType.Center) {
            curvedText(
                text = "${historyHours}$hourUnit",
                color = SecondaryText,
                fontSize = 11.sp
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val context = LocalContext.current

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            context.startActivity(
                                Intent(context, MainMenuActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                                }
                            )
                        })
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${bgData.sgvString}${bgData.slopeArrow}\uFE0E",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = bgColor(bgData.sgvLevel)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(horizontalAlignment = Alignment.Start) {
                        Text(
                            text = bgData.delta,
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                        Text(
                            text = "${ageMin}$minUnit",
                            fontSize = 12.sp,
                            color = ageColor(ageMs)
                        )
                    }
                }

                val hasTT = statusData.tempTargetDuration >= 0
                val targetText = if (hasTT) {
                    "${statusData.tempTarget} (${formatTtDuration(statusData.tempTargetDuration, hourUnit)})"
                } else {
                    statusData.tempTarget
                }
                val insulinUnit = stringResource(R.string.insulin_unit_short)
                val basalText = displayFormat.basalRateSymbol().trimEnd() + statusData.currentBasal.replaceFirst(" ", "")
                val combinedLen = "${statusData.iobSum}$insulinUnit".length +
                    statusData.cob.length +
                    basalText.length +
                    targetText.length
                val statsFontSize = if (combinedLen > 30) 11.sp else 12.sp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "${statusData.iobSum}$insulinUnit", fontSize = statsFontSize, color = IobColor)
                    Text(text = statusData.cob, fontSize = statsFontSize, color = CarbsColor)
                    Text(text = basalText, fontSize = statsFontSize, color = BasalColor)
                    Text(text = targetText, fontSize = statsFontSize, color = when (statusData.tempTargetLevel) {
                        1    -> AutosensTargetColor
                        2    -> TempTargetColor
                        else -> SecondaryText
                    })
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable { windowIdx = (windowIdx + 1) % historyHoursCycle.size }
            ) {
                renderBgGraph(data, historyHours)
            }
        }
    }
}
