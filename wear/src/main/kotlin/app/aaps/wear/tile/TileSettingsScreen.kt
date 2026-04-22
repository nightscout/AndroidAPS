package app.aaps.wear.tile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.ScrollIndicator
import androidx.wear.compose.material3.Text

data class TileSettingOption(val value: String, val label: String)

data class TileSettingRow(
    val label: String,
    val currentValue: String,
    val options: List<TileSettingOption>,
    val onSelect: (String) -> Unit,
)

private val SettingBg = Color(0xFF1C1C1C)
private val SelectedBg = Color(0xFF1A2E4A)
private val SettingSecondary = Color(0xFFAAAAAA)
private val SelectedAccent = Color(0xFF90CAF9)

@Composable
fun TileSettingsScreen(title: String, rows: List<TileSettingRow>) {
    var pickingIdx by remember { mutableStateOf<Int?>(null) }
    val scrollState = rememberScrollState()

    BackHandler(enabled = pickingIdx != null) { pickingIdx = null }

    val idx = pickingIdx
    if (idx != null) {
        OptionPickerScreen(row = rows[idx]) { value ->
            rows[idx].onSelect(value)
            pickingIdx = null
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .verticalScroll(scrollState)
                    .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = SettingSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                rows.forEachIndexed { i, row ->
                    val currentLabel = row.options.find { it.value == row.currentValue }?.label ?: row.currentValue
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SettingBg, shape = RoundedCornerShape(8.dp))
                            .clickable { pickingIdx = i }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(text = row.label, fontSize = 11.sp, color = SettingSecondary)
                        Text(text = currentLabel, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
            if (scrollState.maxValue > 0) {
                ScrollIndicator(
                    state = scrollState,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

@Composable
private fun OptionPickerScreen(row: TileSettingRow, onPick: (String) -> Unit) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .verticalScroll(scrollState)
                .padding(start = 8.dp, end = 8.dp, top = 20.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = row.label,
                fontSize = 14.sp,
                color = SettingSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            row.options.forEach { option ->
                val isSelected = option.value == row.currentValue
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) SelectedBg else SettingBg, shape = RoundedCornerShape(8.dp))
                        .clickable { onPick(option.value) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = option.label,
                        fontSize = 14.sp,
                        color = if (isSelected) SelectedAccent else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
        ScrollIndicator(
            state = scrollState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}
