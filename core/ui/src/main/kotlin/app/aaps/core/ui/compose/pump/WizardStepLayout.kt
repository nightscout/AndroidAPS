package app.aaps.core.ui.compose.pump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.compose.AapsSpacing

@Immutable
data class WizardButton(
    val text: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val loading: Boolean = false
)

@Composable
fun WizardStepLayout(
    primaryButton: WizardButton? = null,
    secondaryButton: WizardButton? = null,
    scrollable: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AapsSpacing.extraLarge)
    ) {
        // Content area (scrollable by default, non-scrollable for content using weight)
        Column(
            modifier = Modifier
                .weight(1f)
                .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = AapsSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
            content = content
        )

        // Bottom buttons pinned
        if (primaryButton != null || secondaryButton != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AapsSpacing.extraLarge),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)
            ) {
                secondaryButton?.let { btn ->
                    OutlinedButton(
                        onClick = btn.onClick,
                        enabled = btn.enabled && !btn.loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(btn.text)
                    }
                }
                primaryButton?.let { btn ->
                    Button(
                        onClick = btn.onClick,
                        enabled = btn.enabled && !btn.loading,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (btn.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(btn.text)
                        }
                    }
                }
            }
        }
    }
}
