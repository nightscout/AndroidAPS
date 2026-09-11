package app.aaps.ui.compose.overview.chips

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.interfaces.overview.graph.TbrState

@Preview(showBackground = true)
@Composable
internal fun TbrChipHighPreview() {
    MaterialTheme { TbrChip(state = TbrState.HIGH, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
internal fun TbrChipLowPreview() {
    MaterialTheme { TbrChip(state = TbrState.LOW, onClick = {}) }
}

@Preview(showBackground = true)
@Composable
internal fun TbrChipNonePreview() {
    MaterialTheme { TbrChip(state = TbrState.NONE, onClick = {}) }
}
