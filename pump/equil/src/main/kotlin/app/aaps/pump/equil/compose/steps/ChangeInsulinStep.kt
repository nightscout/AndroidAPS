package app.aaps.pump.equil.compose.steps

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.pump.WizardButton
import app.aaps.core.ui.compose.pump.WizardErrorBanner
import app.aaps.core.ui.compose.pump.WizardStepLayout
import app.aaps.pump.equil.R
import app.aaps.pump.equil.compose.EquilUiConstants
import app.aaps.pump.equil.compose.EquilWizardViewModel
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage

@Composable
internal fun ChangeInsulinStep(
    viewModel: EquilWizardViewModel,
    onCancel: () -> Unit
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    ChangeInsulinStepContent(
        isLoading = isLoading,
        errorMessage = errorMessage,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = { viewModel.startChangeInsulin() },
        onCancel = onCancel
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun ChangeInsulinStepContent(
    isLoading: Boolean,
    errorMessage: String?,
    @DrawableRes imageRes: Int,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onNext,
            loading = isLoading
        ),
        secondaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.cancel),
            onClick = onCancel
        )
    ) {
        Text(
            text = stringResource(R.string.equil_change_content),
            style = MaterialTheme.typography.bodyLarge
        )
        GlideImage(
            model = imageRes,
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = EquilUiConstants.GIF_MAX_HEIGHT),
            alignment = Alignment.Center,
            contentScale = ContentScale.Fit
        )
        if (errorMessage != null) {
            WizardErrorBanner(message = errorMessage)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ChangeInsulinStepPreview() {
    ChangeInsulinStepContent(
        isLoading = false,
        errorMessage = null,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = {},
        onCancel = {}
    )
}

@Preview(showBackground = true)
@Composable
private fun ChangeInsulinStepLoadingPreview() {
    ChangeInsulinStepContent(
        isLoading = true,
        errorMessage = null,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNext = {},
        onCancel = {}
    )
}
