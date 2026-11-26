package app.aaps.ui.activities

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R

/** The launcher icon badge, as [ErrorActivity] passes it. */
private val badge: @Composable () -> Unit = {
    Image(
        painter = painterResource(id = R.mipmap.ic_launcher),
        contentDescription = "App icon",
        modifier = Modifier.size(20.dp)
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
internal fun ErrorScreenPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Pump unreachable",
            status = "Last successful communication 25 minutes ago. Check Bluetooth and pump status.",
            onOk = {},
            onMute = {},
            onMute5Min = {},
            badge = badge
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
internal fun ErrorScreenShortPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Bolus error",
            status = "Delivery failed.",
            onOk = {},
            onMute = {},
            onMute5Min = {},
            badge = badge
        )
    }
}

/** The pump-alert shape of the same screen: an action button first, then one button per silence length. */
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
internal fun ErrorScreenAlertPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Occlusion",
            status = "Insulin delivery has stopped. Replace the patch.",
            onOk = {},
            onMute = {},
            onMute5Min = {},
            actionLabel = "Deactivate Patch",
            silenceLengths = listOf(15, 30),
            badge = badge
        )
    }
}

/** A BG alarm: one tap per length, "On every phone", and "Silence all BG alarms". */
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
internal fun ErrorScreenBgAlarmPreview() {
    MaterialTheme {
        ErrorScreen(
            title = "Low",
            status = "3.7 mmol/l",
            onOk = {},
            onMute = {},
            onMute5Min = {},
            silenceLengths = listOf(15, 30, 60),
            silenceEverywhere = false,
            onSilenceAll = {},
            silenceAllMinutes = 60,
            onSilenceKind = {},
            badge = badge
        )
    }
}
