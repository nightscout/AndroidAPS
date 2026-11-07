package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle

/**
 * A label and a switch that act and read as one control.
 *
 * The usual way of writing this - a clickable Row with a Text and a Switch that has its own
 * `onCheckedChange` - gives a screen reader two stops: the label with no state ("Start eating soon
 * TT, double-tap to activate"), then a switch with no name ("Off, switch"). Here the whole row is
 * the switch, so it reads "Start eating soon TT, switch, off" and says the new state after a
 * double-tap. The Switch inside is drawn only.
 */
@Composable
fun LabeledSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelColor: Color = Color.Unspecified,
    labelStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    leadingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        leadingContent?.invoke()
        Text(text = label, style = labelStyle, color = labelColor, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
