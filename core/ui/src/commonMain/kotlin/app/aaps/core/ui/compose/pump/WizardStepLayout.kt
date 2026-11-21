package app.aaps.core.ui.compose.pump

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.stringResource

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
            // Most waits in a pump wizard end the same way: the primary button (Next, Retry, Pair)
            // appears or becomes enabled - priming done, pod activated, a device picked. On screen
            // that is obvious; a screen reader said nothing. Announce the button when it becomes
            // usable, so the user knows the step is ready. A plain Row does not merge, so a pane
            // title is safe here.
            val readyButton = primaryButton?.takeIf { it.enabled && !it.loading }?.text
            // Both buttons get the height of the taller one, and a label on two lines stays centred.
            // A long label wraps in some languages ("Aplikátor odstraněn" in Czech), and without this
            // the two buttons had different heights and the wrapped text was aligned to the left.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (readyButton != null) Modifier.announceWhenShown(readyButton) else Modifier)
                    .padding(vertical = AapsSpacing.extraLarge)
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(AapsSpacing.large)
            ) {
                secondaryButton?.let { btn ->
                    OutlinedButton(
                        onClick = btn.onClick,
                        enabled = btn.enabled && !btn.loading,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(btn.text, textAlign = TextAlign.Center)
                    }
                }
                primaryButton?.let { btn ->
                    // While busy only a spinner is drawn, so a screen reader heard "Button, disabled"
                    // with no name. It keeps its name and says it is busy.
                    val busy = stringResource(CoreUiStrings.please_wait)
                    Button(
                        onClick = btn.onClick,
                        enabled = btn.enabled && !btn.loading,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .then(
                                if (btn.loading) Modifier.semantics {
                                    contentDescription = btn.text
                                    stateDescription = busy
                                }
                                else Modifier
                            )
                    ) {
                        if (btn.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Text(btn.text, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}
