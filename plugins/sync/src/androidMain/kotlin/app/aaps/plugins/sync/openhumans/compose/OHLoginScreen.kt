package app.aaps.plugins.sync.openhumans.compose

import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.stringResource
import app.aaps.core.ui.CoreUiStrings
import app.aaps.plugins.sync.SyncStrings
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
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
                title = { Text(stringResource(SyncStrings.open_humans)) },
                navigationIcon = {
                    if (state != OHLoginViewModel.State.FINISHING) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(CoreUiStrings.close)
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

/**
 * @see WelcomeStepPreview
 */
@Composable
internal fun WelcomeStep(onNext: () -> Unit) {
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
            text = stringResource(SyncStrings.welcome_to_open_humans),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Text(
            text = stringResource(SyncStrings.open_humans_description),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Text(
            text = stringResource(SyncStrings.setup_data_upload),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Button(
            onClick = onNext,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        ) {
            Text(stringResource(SyncStrings.next))
        }
    }
}

/**
 * @see ConsentStepPreview
 */
@Composable
internal fun ConsentStep(authUrl: String) {
    val context = LocalContext.current
    var accepted by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AapsSpacing.extraLarge)
    ) {
        Text(
            text = stringResource(SyncStrings.consent),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = stringResource(SyncStrings.please_read__information),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = AapsSpacing.small)
        )

        Spacer(Modifier.height(AapsSpacing.extraLarge))

        // Terms of use
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(AapsSpacing.extraLarge)) {
                Text(
                    text = stringResource(SyncStrings.terms_of_use),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(AapsSpacing.medium))
                Text(
                    text = stringResource(SyncStrings.info_openhumans),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(Modifier.height(AapsSpacing.large))

        // Data uploaded
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(AapsSpacing.extraLarge)) {
                Text(
                    text = stringResource(SyncStrings.data_uploaded),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(AapsSpacing.medium))
                DataList(
                    listOf(
                        SyncStrings.glucose_values,
                        SyncStrings.boluses,
                        SyncStrings.extended_boluses,
                        SyncStrings.carbohydrates,
                        SyncStrings.careportal_events,
                        SyncStrings.profile_switches,
                        SyncStrings.total_daily_doses,
                        SyncStrings.temporary_basal_rates,
                        SyncStrings.temporary_targets,
                        SyncStrings.settings,
                        SyncStrings.application_version,
                        SyncStrings.device_model,
                        SyncStrings.screen_dimensions,
                        SyncStrings.algorithm_debug_data
                    )
                )
            }
        }

        Spacer(Modifier.height(AapsSpacing.large))

        // Data NOT uploaded
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(modifier = Modifier.padding(AapsSpacing.extraLarge)) {
                Text(
                    text = stringResource(SyncStrings.data_not_uploaded),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(AapsSpacing.medium))
                DataList(
                    listOf(
                        SyncStrings.passwords,
                        SyncStrings.nightscout_url,
                        SyncStrings.nightscout_api_secret,
                        SyncStrings.free_text_fields
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
                text = stringResource(SyncStrings.agree),
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
            Text(stringResource(SyncStrings.login_open_humans))
        }
    }
}

@Composable
private fun DataList(items: List<TextRef>) {
    val text = items.map { stringResource(it) }.joinToString(separator = "  \u2022  ")
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium
    )
}

/**
 * @see ConfirmStepPreview
 */
@Composable
internal fun ConfirmStep(
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
            text = stringResource(SyncStrings.final_touches),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(SyncStrings.uploading_proceed),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(AapsSpacing.extraLarge),
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        ) {
            OutlinedButton(onClick = onCancel) {
                Text(stringResource(SyncStrings.cancel))
            }
            Button(onClick = onProceed) {
                Text(stringResource(SyncStrings.proceed))
            }
        }
    }
}

/**
 * @see FinishingStepPreview
 */
@Composable
internal fun FinishingStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(AapsSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(SyncStrings.finishing),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(SyncStrings.this_may_take_a_few_seconds),
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

/**
 * @see DoneStepPreview
 */
@Composable
internal fun DoneStep(onClose: () -> Unit) {
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
            text = stringResource(SyncStrings.we_re_done),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.extraLarge)
        )

        Text(
            text = stringResource(SyncStrings.silently_upload_date_note),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        )

        Button(
            onClick = onClose,
            modifier = Modifier.padding(top = AapsSpacing.medium)
        ) {
            Text(stringResource(SyncStrings.close))
        }
    }
}
