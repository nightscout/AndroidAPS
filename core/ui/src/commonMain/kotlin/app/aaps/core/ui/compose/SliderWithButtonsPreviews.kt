package app.aaps.core.ui.compose

import app.aaps.core.ui.UiStrings
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import org.jetbrains.compose.ui.tooling.preview.Preview
import app.aaps.core.data.format.NumberFormat
import app.aaps.core.keys.interfaces.TextRef

@Preview(showBackground = true)
@Composable
internal fun SliderWithButtonsPreview() {
    MaterialTheme {
        SliderWithButtons(
            value = 5.0,
            onValueChange = {},
            valueRange = 0.0..10.0,
            step = 0.5
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SliderWithButtonsValuePreview() {
    MaterialTheme {
        SliderWithButtons(
            value = 3.5,
            onValueChange = {},
            valueRange = 0.0..10.0,
            step = 0.1,
            showValue = true,
            valueFormat = NumberFormat.DECIMAL_1,
            unitLabel = TextRef.Literal("U")
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SliderWithButtonsIntPreview() {
    MaterialTheme {
        SliderWithButtons(
            value = 45.0,
            onValueChange = {},
            valueRange = 0.0..120.0,
            step = 5.0,
            showValue = true,
            valueFormat = NumberFormat.INTEGER,
            unitLabel = UiStrings.units_min
        )
    }
}

@Preview(showBackground = true)
@Composable
internal fun SliderWithButtonsNonLinearPreview() {
    MaterialTheme {
        Column {
            SliderWithButtons(
                value = 50.0,
                onValueChange = {},
                valueRange = 0.0..500.0,
                step = 1.0,
                controlPoints = listOf(
                    0.0 to 0.0,
                    0.5 to 50.0,
                    1.0 to 500.0
                ),
                showValue = true,
                valueFormat = NumberFormat.INTEGER
            )
            SliderWithButtons(
                value = 250.0,
                onValueChange = {},
                valueRange = 0.0..500.0,
                step = 1.0,
                controlPoints = listOf(
                    0.0 to 0.0,
                    0.5 to 50.0,
                    1.0 to 500.0
                ),
                showValue = true,
                valueFormat = NumberFormat.INTEGER
            )
        }
    }
}
