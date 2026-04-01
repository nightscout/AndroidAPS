package app.aaps.ui.compose.main

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.aaps.ui.search.M3SearchBar
import app.aaps.ui.search.SearchUiState

/**
 * Main top bar with M3-style search bar.
 * Layout: [Menu] [----Search Bar----] [Settings]
 *
 * @param searchUiState Current search UI state
 * @param onMenuClick Called when menu button is clicked
 * @param onPreferencesClick Called when preferences button is clicked
 * @param onSearchQueryChange Called when search query changes
 * @param onSearchClear Called when search query is cleared
 * @param onSearchActiveChange Called when search active state changes
 * @param modifier Modifier for the component
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainTopBar(
    searchUiState: SearchUiState,
    onMenuClick: () -> Unit,
    onPreferencesClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchClear: () -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                M3SearchBar(
                    query = searchUiState.query,
                    isActive = searchUiState.isSearchActive,
                    onQueryChange = onSearchQueryChange,
                    onClearClick = onSearchClear,
                    onActiveChange = onSearchActiveChange,
                    modifier = Modifier.weight(1f)
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.open_navigation)
                )
            }
        },
        actions = {
            IconButton(onClick = onPreferencesClick) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(app.aaps.core.ui.R.string.settings)
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent
        ),
        modifier = modifier
    )
}
