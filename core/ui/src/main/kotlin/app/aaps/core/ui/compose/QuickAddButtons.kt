package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun QuickAddButtons(
    increment1: Int,
    increment2: Int,
    increment3: Int,
    onAddCarbs: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val increments = listOf(increment1, increment2, increment3).filter { it > 0 }
    if (increments.isEmpty()) return

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        increments.forEach { amount ->
            FilledTonalButton(onClick = { onAddCarbs(amount) }) {
                Text("+$amount")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun QuickAddButtonsPreview() {
    MaterialTheme {
        QuickAddButtons(increment1 = 5, increment2 = 10, increment3 = 20, onAddCarbs = {})
    }
}
