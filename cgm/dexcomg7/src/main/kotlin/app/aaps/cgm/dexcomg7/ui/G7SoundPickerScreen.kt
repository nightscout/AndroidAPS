package app.aaps.cgm.dexcomg7.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import app.aaps.cgm.dexcomg7.R
import app.aaps.cgm.dexcomg7.alarm.G7AlarmSound
import app.aaps.cgm.dexcomg7.alarm.G7SoundLevel
import app.aaps.cgm.dexcomg7.alarm.G7SoundSet
import app.aaps.cgm.dexcomg7.alarm.G7SoundTheme
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.stringResource as textRefString
import kotlin.math.roundToInt

/**
 * Picks the sound of one alarm, the way Trio does: one row per style of the sound set made for this
 * alarm, and under the chosen style a slider for its urgency level. The extra tones are folded away
 * below. Moving the slider plays the new level, because the point is to hear the difference.
 *
 * Every pick is saved at once with [onSelect]. [playing] is the sound playing now, for the Play and
 * Stop buttons.
 */
@Composable
fun G7SoundPickerScreen(
    title: String,
    set: G7SoundSet,
    initial: G7AlarmSound,
    playing: G7AlarmSound?,
    onSelect: (G7AlarmSound) -> Unit,
    onPlay: (G7AlarmSound) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit
) {
    var selected by remember { mutableStateOf(initial) }
    var showTones by remember { mutableStateOf(initial.set == null) }
    DisposableEffect(Unit) { onDispose { onStop() } }

    fun select(sound: G7AlarmSound, play: Boolean) {
        if (sound == selected) return
        selected = sound
        onSelect(sound)
        if (play) onPlay(sound)
    }

    val togglePlay: (G7AlarmSound) -> Unit = { if (playing == it) onStop() else onPlay(it) }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = textRefString(CoreUiStrings.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AapsSpacing.extraLarge, vertical = AapsSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.small)
        ) {
            SectionTitle(stringResource(R.string.dexcom_g7_sound_for_this_alarm))
            Text(
                stringResource(R.string.dexcom_g7_sound_preview_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            G7SoundTheme.entries.forEach { theme ->
                // Changing the style keeps the urgency level.
                val sound = if (selected.theme == theme) selected else G7AlarmSound.of(set, theme, selected.level ?: G7AlarmSound.defaultLevel)
                SoundRow(
                    label = stringResource(theme.label),
                    isSelected = selected.theme == theme,
                    isPlaying = playing == sound,
                    onClick = { select(sound, play = false) },
                    onTogglePlay = { togglePlay(sound) }
                )
                if (selected.theme == theme) {
                    UrgencySlider(level = sound.level ?: G7AlarmSound.defaultLevel) { select(G7AlarmSound.of(set, theme, it), play = true) }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = AapsSpacing.medium))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTones = !showTones }
                    .padding(vertical = AapsSpacing.medium)
            ) {
                Text(stringResource(R.string.dexcom_g7_sound_other), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (showTones) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null)
            }
            if (showTones) {
                G7AlarmSound.tones.forEach { tone ->
                    SoundRow(
                        label = stringResource(tone.label),
                        isSelected = selected == tone,
                        isPlaying = playing == tone,
                        onClick = { select(tone, play = false) },
                        onTogglePlay = { togglePlay(tone) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SoundRow(label: String, isSelected: Boolean, isPlaying: Boolean, onClick: () -> Unit, onTogglePlay: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        IconButton(onClick = onTogglePlay) {
            Icon(
                if (isPlaying) Icons.Filled.StopCircle else Icons.Filled.PlayCircle,
                contentDescription = stringResource(if (isPlaying) R.string.dexcom_g7_sound_stop else R.string.dexcom_g7_sound_play),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Mild, medium or harsh, in three steps. */
@Composable
private fun UrgencySlider(level: G7SoundLevel, onChange: (G7SoundLevel) -> Unit) {
    val levels = G7SoundLevel.entries
    val levelName = stringResource(level.label)
    val sliderName = stringResource(R.string.dexcom_g7_sound_urgency)
    Column(modifier = Modifier.padding(start = AapsSpacing.xxLarge * 2, end = AapsSpacing.medium)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(sliderName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(levelName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = level.ordinal.toFloat(),
            onValueChange = { raw ->
                val picked = levels[raw.roundToInt().coerceIn(0, levels.lastIndex)]
                if (picked != level) onChange(picked)
            },
            valueRange = 0f..levels.lastIndex.toFloat(),
            steps = levels.size - 2,
            modifier = Modifier.semantics {
                contentDescription = sliderName
                stateDescription = levelName
            }
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            levels.forEach {
                Text(stringResource(it.label), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
