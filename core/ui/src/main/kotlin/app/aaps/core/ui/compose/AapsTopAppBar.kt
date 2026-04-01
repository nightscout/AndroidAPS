package app.aaps.core.ui.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
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
 * Uses [MaterialTheme.colorScheme.surfaceContainer] which provides visible
 * elevation contrast in both light and dark themes through Material 3's
 * tonal elevation system.
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

/**
 * AndroidAPS TopAppBar component with scroll behavior support.
 *
 * Uses [MaterialTheme.colorScheme.surfaceContainer] which provides visible
 * elevation contrast in both light and dark themes through Material 3's
 * tonal elevation system.
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
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun AapsTopAppBarPreview() {
    AapsTheme {
        AapsTopAppBar(
            title = { Text("Screen Title") }
        )
    }
}
