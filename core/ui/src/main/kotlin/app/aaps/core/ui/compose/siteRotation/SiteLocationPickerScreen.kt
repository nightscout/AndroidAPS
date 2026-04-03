package app.aaps.core.ui.compose.siteRotation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.TE
import app.aaps.core.ui.R

/**
 * Full-screen wrapper for [SiteLocationPicker] with a top bar and confirm button.
 * Used when navigating from a dialog (Fill, Care) to pick a site location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteLocationPickerScreen(
    siteType: TE.Type,
    bodyType: BodyType,
    onClose: () -> Unit,
    onLocationConfirmed: (TE.Location, TE.Arrow) -> Unit,
    entries: List<TE> = emptyList()
) {
    var selectedLocation by rememberSaveable { mutableStateOf(TE.Location.NONE) }
    var selectedArrow by rememberSaveable { mutableStateOf(TE.Arrow.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_rotation)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { onLocationConfirmed(selectedLocation, selectedArrow) },
                        enabled = selectedLocation != TE.Location.NONE
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.save),
                            tint = if (selectedLocation != TE.Location.NONE)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        SiteLocationPicker(
            siteType = siteType,
            bodyType = bodyType,
            entries = entries,
            selectedLocation = selectedLocation,
            selectedArrow = selectedArrow,
            onLocationSelected = { selectedLocation = it },
            onArrowSelected = { selectedArrow = it },
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SiteLocationPickerScreenPreview() {
    MaterialTheme {
        SiteLocationPickerScreen(
            siteType = TE.Type.CANNULA_CHANGE,
            bodyType = BodyType.MAN,
            onClose = {},
            onLocationConfirmed = { _, _ -> }
        )
    }
}
