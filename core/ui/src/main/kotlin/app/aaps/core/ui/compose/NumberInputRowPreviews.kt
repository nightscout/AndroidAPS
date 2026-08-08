package app.aaps.core.ui.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.R
import app.aaps.core.keys.R as KeysR

@Preview(showBackground = true)
@Composable
internal fun NumberInputRowBasicPreview() {
    MaterialTheme {
        NumberInputRow(labelResId = R.string.carbs, value = 20.0, onValueChange = {}, valueRange = 0.0..100.0, step = 1.0)
    }
}

@Preview(showBackground = true)
@Composable
internal fun NumberInputRowWithUnitPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = R.string.insulin_label,
            value = 3.5,
            onValueChange = {},
            valueRange = 0.0..10.0,
            step = 0.1,
            decimalPlaces = 1,
            unitLabel = TextRef.Literal("U")
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun NumberInputRowMinutesPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = R.string.duration,
            value = 130.0,
            onValueChange = {},
            valueRange = 0.0..300.0,
            step = 10.0,
            unitLabel = TextRef.Res(KeysR.string.units_min)
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun NumberInputRowPercentPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = R.string.duration,
            value = 100.0,
            onValueChange = {},
            valueRange = 10.0..200.0,
            step = 5.0,
            unitLabel = TextRef.Res(KeysR.string.units_percent)
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun NumberInputRowMinutesDirectPreview() {
    MaterialTheme {
        NumberInputRow(
            labelResId = R.string.duration,
            value = 130.0,
            onValueChange = {},
            valueRange = 0.0..300.0,
            step = 10.0,
            unitLabel = TextRef.Res(KeysR.string.units_min)
        )
    }
}
