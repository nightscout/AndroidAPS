package app.aaps.pump.carelevo.compose.patchflow

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import app.aaps.core.ui.compose.AapsCard
import app.aaps.pump.carelevo.R
import app.aaps.pump.carelevo.presentation.type.CarelevoPatchStep
import app.aaps.pump.carelevo.presentation.type.CarelevoScreenType

@Composable
internal fun CarelevoPatchFlowPlaceholder(
    title: String,
    description: String,
    todo: String
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Top
    ) {
        AapsCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
                Text(text = description, style = MaterialTheme.typography.bodyLarge)
                Text(text = todo, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = "Back navigation is enabled so the patch flow shell can be tested before each step is fully ported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun patchFlowTitle(screenType: CarelevoScreenType): String =
    when (screenType) {
        CarelevoScreenType.CONNECTION_FLOW_START -> "Patch Setup"
        CarelevoScreenType.COMMUNICATION_CHECK   -> "Communication Check"
        CarelevoScreenType.PATCH_DISCARD         -> "Deactivate Patch"
        CarelevoScreenType.SAFETY_CHECK          -> "Safety Check"
        CarelevoScreenType.NEEDLE_INSERTION      -> "Insert Needle"
    }

@Composable
internal fun patchStepTitle(step: CarelevoPatchStep): String =
    when (step) {
        CarelevoPatchStep.PATCH_START      -> stringResource(R.string.carelevo_connect_prepare_title)
        CarelevoPatchStep.PATCH_CONNECT    -> stringResource(R.string.carelevo_connect_patch_title)
        CarelevoPatchStep.SAFETY_CHECK     -> stringResource(R.string.carelevo_connect_safety_check_title)
        CarelevoPatchStep.PATCH_ATTACH     -> stringResource(R.string.carelevo_connect_patch_attach_title)
        CarelevoPatchStep.NEEDLE_INSERTION -> stringResource(R.string.carelevo_connect_needle_check_title)
    }

@Composable
internal fun PatchFlowButtonText(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

internal fun hasPatchStartPermissions(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasPermission(context, Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasPermission(context: Context, permissionType: String): Boolean =
    ContextCompat.checkSelfPermission(context, permissionType) == PackageManager.PERMISSION_GRANTED

internal fun requestPatchStartPermissions(activity: ComponentActivity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
            100
        )
    } else {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            100
        )
    }
}
