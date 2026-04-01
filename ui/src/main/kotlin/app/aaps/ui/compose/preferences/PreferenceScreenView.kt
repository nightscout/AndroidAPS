package app.aaps.ui.compose.preferences

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposeScreenContent
import app.aaps.core.ui.compose.LocalSnackbarHostState
import app.aaps.core.ui.compose.preference.LocalHighlightKey
import app.aaps.core.ui.compose.preference.LocalNavigateToCompose
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.core.ui.compose.preference.ProvidePreferenceTheme
import app.aaps.core.ui.compose.preference.SectionLevel
import app.aaps.core.ui.compose.preference.addPreferenceContent
import app.aaps.core.ui.compose.preference.rememberPreferenceSectionState
import app.aaps.core.ui.compose.preference.verticalScrollIndicators

/**
 * Screen that displays a preference screen definition.
 * Used when navigating from search results to a specific screen.
 *
 * @param screenDef The preference screen definition to display
 * @param highlightKey Optional preference key to highlight (for search navigation)
 * @param onBackClick Callback when back button is clicked
 */
@Composable
fun PreferenceScreenView(
    screenDef: PreferenceSubScreenDef,
    highlightKey: String? = null,
    onBackClick: () -> Unit
) {
    val title = if (screenDef.titleResId != 0) {
        stringResource(screenDef.titleResId)
    } else {
        screenDef.key
    }

    val sectionState = rememberPreferenceSectionState()
    val snackbarHostState = remember { SnackbarHostState() }
    var composeScreen: ComposeScreenContent? by remember { mutableStateOf(null) }

    // Auto-expand the main section
    LaunchedEffect(screenDef.key) {
        sectionState.toggle("${screenDef.key}_main", SectionLevel.TOP_LEVEL)
    }

    BackHandler(enabled = composeScreen != null) {
        composeScreen = null
    }

    composeScreen?.let { screen ->
        screen.Content(onBack = { composeScreen = null })
        return
    }

    CompositionLocalProvider(
        LocalSnackbarHostState provides snackbarHostState,
        LocalHighlightKey provides highlightKey,
        LocalNavigateToCompose provides { screen -> composeScreen = screen }
    ) {
        ProvidePreferenceTheme {
            Scaffold(
                topBar = {
                    AapsTopAppBar(
                        title = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(app.aaps.core.ui.R.string.back)
                                )
                            }
                        }
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { paddingValues ->
                val listState = rememberLazyListState()
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScrollIndicators(listState),
                    state = listState
                ) {
                    addPreferenceContent(
                        content = screenDef,
                        sectionState = sectionState
                    )
                }
            }
        }
    }
}
