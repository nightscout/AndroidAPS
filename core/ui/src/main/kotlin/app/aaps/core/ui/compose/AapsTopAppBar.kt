package app.aaps.core.ui.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/**
 * AndroidAPS TopAppBar component with proper elevation visibility in dark mode.
 *
 * **Always use this instead of Material 3's bare [TopAppBar].** Direct uses of
 * `TopAppBar` mix differing default colors and break the consistent status-bar
 * appearance once edge-to-edge is enabled (the bar's container color extends
 * behind the status bar).
 *
 * Uses [MaterialTheme.colorScheme.surface] for the resting state and
 * [MaterialTheme.colorScheme.surfaceContainer] for the scrolled state — a
 * "seamless" look where the bar blends into the screen background, with a
 * subtle one-step elevation when content scrolls underneath.
 *
 * ## Usage convention
 *
 * - **`title`** — pass a plain `Text(...)` only. Do not embed icons in the
 *   title (no `Row { Icon; Text }` patterns). Material 3 reserves icons for
 *   the `navigationIcon` and `actions` slots.
 * - **`navigationIcon`** — choose by screen role:
 *     - **`Icons.Filled.Close` (✕)** for *modal tasks* the user either commits
 *       or abandons (any screen with a Save / OK / Confirm / Activate button —
 *       dialogs, editors, wizards, pickers). Backing out = cancel.
 *     - **`Icons.AutoMirrored.Filled.ArrowBack` (←)** for *pure navigation*
 *       (browse / list / settings / management screens with no commit button).
 *       Backing out = go to previous screen.
 *     - **`Icons.Filled.Menu` (≡)** only at top level (drawer entry).
 * - **`actions`** — keep to 2–3 icons max, right-aligned.
 *
 * @param title The title to be displayed in the center of the TopAppBar
 * @param modifier Modifier to be applied to the TopAppBar
 * @param navigationIcon The navigation icon displayed at the start of the TopAppBar
 * @param actions The actions displayed at the end of the TopAppBar
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AapsTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

/**
 * AndroidAPS TopAppBar component with scroll behavior support.
 *
 * **Always use this instead of Material 3's bare [TopAppBar]** — see the
 * single-arg overload above for the rationale.
 *
 * Uses [MaterialTheme.colorScheme.surface] for the resting state and
 * [MaterialTheme.colorScheme.surfaceContainer] for the scrolled state — a
 * "seamless" look where the bar blends into the screen background, with a
 * subtle one-step elevation when content scrolls underneath.
 *
 * @param title The title to be displayed in the center of the TopAppBar
 * @param scrollBehavior Scroll behavior for collapsing/expanding behavior
 * @param modifier Modifier to be applied to the TopAppBar
 * @param navigationIcon The navigation icon displayed at the start of the TopAppBar
 * @param actions The actions displayed at the end of the TopAppBar
 */
@ExperimentalMaterial3Api
@Composable
fun AapsTopAppBar(
    title: @Composable () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun AapsTopAppBarPreview() {
    MaterialTheme {
        AapsTopAppBar(
            title = { Text("Screen Title") }
        )
    }
}

@Preview(showBackground = true, name = "Light - Nav & Actions")
@Composable
private fun AapsTopAppBarWithNavAndActionsPreview() {
    MaterialTheme {
        AapsTopAppBar(
            title = { Text("Screen Title") },
            navigationIcon = {
                IconButton(onClick = {}) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                }
            },
            actions = {
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Filled.Settings, contentDescription = null)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, contentDescription = null)
                }
            }
        )
    }
}
