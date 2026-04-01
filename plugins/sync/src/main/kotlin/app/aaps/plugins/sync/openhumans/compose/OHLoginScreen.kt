package app.aaps.plugins.sync.openhumans.compose

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.openhumans.ui.OHLoginViewModel

@Composable
internal fun OHLoginScreen(
    viewModel: OHLoginViewModel,
    authUrl: String,
    onFinishActivity: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onBack = { if (!viewModel.goBack()) onFinishActivity() }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            AapsTopAppBar(
                title = { Text(stringResource(R.string.open_humans)) },
                navigationIcon = {
                    if (state != OHLoginViewModel.State.FINISHING) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        AnimatedContent(
            targetState = state,
            label = "login_wizard",
            modifier = Modifier.padding(paddingValues)
        ) { currentState ->
            when (currentState) {
                OHLoginViewModel.State.WELCOME -> WelcomeStep(onNext = { viewModel.goToConsent() })
                OHLoginViewModel.State.CONSENT -> ConsentStep(authUrl = authUrl)
                OHLoginViewModel.State.CONFIRM -> ConfirmStep(
                    onCancel = { viewModel.cancel() },
                    onProceed = { viewModel.finish() }
                )

                OHLoginViewModel.State.FINISHING -> FinishingStep()
                OHLoginViewModel.State.DONE -> DoneStep(onClose = onFinishActivity)
            }
        }
    }
}

@Composable
private fun WelcomeStep(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = rememberVectorPainter(OHLogo),
            contentDescription = null,
            modifier = Modifier.size(160.dp)
        )

        Text(
            text = stringResource(R.string.welcome_to_open_humans),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Text(
            text = stringResource(R.string.open_humans_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Text(
            text = stringResource(R.string.setup_data_upload),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Button(
            onClick = onNext,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        ) {
            Text(stringResource(R.string.next))
        }
    }
}

@Composable
private fun ConsentStep(authUrl: String) {
    val context = LocalContext.current
    var accepted by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge)
    ) {
        Text(
            text = stringResource(R.string.consent),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(R.string.please_read__information),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AapsSpacing.small)
        )

        Spacer(Modifier.height(AapsSpacing.extraLarge))

        // Terms of use
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(AapsSpacing.extraLarge)) {
                Text(
                    text = stringResource(R.string.terms_of_use),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(AapsSpacing.medium))
                Text(
                    text = stringResource(R.string.info_openhumans),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(AapsSpacing.large))

        // Data uploaded
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(AapsSpacing.extraLarge)) {
                Text(
                    text = stringResource(R.string.data_uploaded),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(AapsSpacing.medium))
                DataList(
                    listOf(
                        R.string.glucose_values,
                        R.string.boluses,
                        R.string.extended_boluses,
                        R.string.carbohydrates,
                        R.string.careportal_events,
                        R.string.profile_switches,
                        R.string.total_daily_doses,
                        R.string.temporary_basal_rates,
                        R.string.temporary_targets,
                        R.string.settings,
                        R.string.application_version,
                        R.string.device_model,
                        R.string.screen_dimensions,
                        R.string.algorithm_debug_data
                    )
                )
            }
        }

        Spacer(Modifier.height(AapsSpacing.large))

        // Data NOT uploaded
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(AapsSpacing.extraLarge)) {
                Text(
                    text = stringResource(R.string.data_not_uploaded),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(AapsSpacing.medium))
                DataList(
                    listOf(
                        R.string.passwords,
                        R.string.nightscout_url,
                        R.string.nightscout_api_secret,
                        R.string.free_text_fields
                    )
                )
            }
        }

        Spacer(Modifier.height(AapsSpacing.extraLarge))

        HorizontalDivider()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = AapsSpacing.large)
        ) {
            Text(
                text = stringResource(R.string.agree),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = accepted,
                onCheckedChange = { accepted = it }
            )
        }

        Button(
            onClick = {
                CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authUrl))
            },
            enabled = accepted,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.login_open_humans))
        }
    }
}

@Composable
private fun DataList(items: List<Int>) {
    val text = items.map { stringResource(it) }.joinToString(separator = "  \u2022  ")
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun ConfirmStep(
    onCancel: () -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.final_touches),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.uploading_proceed),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge),
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
            Button(onClick = onProceed) {
                Text(stringResource(R.string.proceed))
            }
        }
    }
}

@Composable
private fun FinishingStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.finishing),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.this_may_take_a_few_seconds),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AapsSpacing.extraLarge)
        )
    }
}

@Composable
private fun DoneStep(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(120.dp)
        )

        Text(
            text = stringResource(R.string.we_re_done),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Text(
            text = stringResource(R.string.silently_upload_date_note),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )

        Button(
            onClick = onClose,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        ) {
            Text(stringResource(R.string.close))
        }
    }
}

@Preview(showBackground = true, name = "Welcome")
@Composable
private fun WelcomeStepPreview() {
    MaterialTheme {
        WelcomeStep(onNext = {})
    }
}

@Preview(showBackground = true, name = "Consent")
@Composable
private fun ConsentStepPreview() {
    MaterialTheme {
        ConsentStep(authUrl = "https://example.com/auth")
    }
}

@Preview(showBackground = true, name = "Confirm")
@Composable
private fun ConfirmStepPreview() {
    MaterialTheme {
        ConfirmStep(onCancel = {}, onProceed = {})
    }
}

@Preview(showBackground = true, name = "Finishing")
@Composable
private fun FinishingStepPreview() {
    MaterialTheme {
        FinishingStep()
    }
}

@Preview(showBackground = true, name = "Done")
@Composable
private fun DoneStepPreview() {
    MaterialTheme {
        DoneStep(onClose = {})
    }
}
