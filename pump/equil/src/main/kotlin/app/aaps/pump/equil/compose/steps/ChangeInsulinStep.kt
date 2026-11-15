package app.aaps.pump.equil.compose.steps

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.banner.ErrorBanner
import app.aaps.core.ui.compose.pump.WizardButton
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
    val newInfusionSet by viewModel.newInfusionSet.collectAsStateWithLifecycle()

    ChangeInsulinStepContent(
        isLoading = isLoading,
        errorMessage = errorMessage,
        newInfusionSet = newInfusionSet,
        imageRes = R.drawable.equil_animation_wizard_detach,
        onNewInfusionSet = { viewModel.selectNewInfusionSet(it) },
        onNext = { viewModel.startChangeInsulin() },
        onCancel = onCancel
    )
}

/**
 * The question decides the rest of the flow (#5199): with a new infusion set the site is recorded
 * and the cannula primed; with only a reservoir or battery change neither happens. It has no default,
 * so Next stays disabled until it is answered.
 *
 * @see ChangeInsulinStepPreview
 * @see ChangeInsulinStepSameInfusionSetPreview
 * @see ChangeInsulinStepLoadingPreview
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
internal fun ChangeInsulinStepContent(
    isLoading: Boolean,
    errorMessage: String?,
    newInfusionSet: Boolean?,
    @DrawableRes imageRes: Int,
    onNewInfusionSet: (Boolean) -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    WizardStepLayout(
        primaryButton = WizardButton(
            text = stringResource(app.aaps.core.ui.R.string.next),
            onClick = onNext,
            loading = isLoading,
            enabled = newInfusionSet != null && !isLoading
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
        Text(
            text = stringResource(R.string.equil_new_infusion_set_question),
            style = MaterialTheme.typography.titleMedium
        )
        Column(modifier = Modifier.selectableGroup()) {
            InfusionSetOption(
                label = stringResource(R.string.equil_new_infusion_set_yes),
                selected = newInfusionSet == true,
                enabled = !isLoading,
                onClick = { onNewInfusionSet(true) }
            )
            InfusionSetOption(
                label = stringResource(R.string.equil_new_infusion_set_no),
                selected = newInfusionSet == false,
                enabled = !isLoading,
                onClick = { onNewInfusionSet(false) }
            )
        }
        if (newInfusionSet == false) {
            Text(
                text = stringResource(R.string.equil_same_infusion_set_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (errorMessage != null) {
            ErrorBanner(message = errorMessage)
        }
    }
}

@Composable
private fun InfusionSetOption(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = AapsSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The whole row is the click target; the button only shows the state.
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = AapsSpacing.medium)
        )
    }
}
