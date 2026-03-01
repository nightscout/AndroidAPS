package app.aaps.core.ui.compose.preference

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.keys.interfaces.PreferenceVisibilityContext
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.ComposeScreenContent

/**
 * Screen for displaying plugin preferences using Compose.
 * Uses the same rendering approach as AllPreferencesScreen - collapsible sections.
 *
 * @param plugin The plugin whose preferences to display
 * @param visibilityContext Context for evaluating visibility conditions
 * @param onBackClick Callback when back button is clicked
 */
@Composable
fun PluginPreferencesScreen(
    plugin: PluginBase,
    visibilityContext: PreferenceVisibilityContext? = null,
    onBackClick: () -> Unit
) {
    val preferenceScreenContent = plugin.getPreferenceScreenContent()
    val title = plugin.name

    // State for inline Compose screen navigation
    var composeScreen: ComposeScreenContent? by remember { mutableStateOf(null) }

    BackHandler(enabled = composeScreen != null) {
        composeScreen = null
    }

    // If a compose sub-screen is active, render it instead of preferences
    composeScreen?.let { screen ->
        screen.Content(onBack = { composeScreen = null })
        return
    }

    ProvidePreferenceTheme {
        CompositionLocalProvider(
            LocalNavigateToCompose provides { screen -> composeScreen = screen }
        ) {
            when (preferenceScreenContent) {
                is PreferenceSubScreenDef -> {
                    // PreferenceSubScreenDef - use same rendering as AllPreferencesScreen
                    SinglePluginPreferencesRenderer(
                        screen = preferenceScreenContent,
                        title = title,
                        plugin = plugin,
                        visibilityContext = visibilityContext,
                        onBackClick = onBackClick
                    )
                }

                else                      -> {
                    // Fallback for plugins without compose preferences
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
                        containerColor = MaterialTheme.colorScheme.background
                    ) { paddingValues ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(paddingValues),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(app.aaps.core.ui.R.string.no_compose_preferences),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renderer for single plugin preferences using the same collapsible section approach as AllPreferencesScreen.
 * Shares the same code path - only difference is it displays ONE screen instead of multiple.
 */
@Composable
private fun SinglePluginPreferencesRenderer(
    screen: PreferenceSubScreenDef,
    title: String,
    plugin: PluginBase,
    visibilityContext: PreferenceVisibilityContext?,
    onBackClick: () -> Unit
) {
    val pluginWithPrefs = plugin as? PluginBaseWithPreferences
    if (pluginWithPrefs == null) {
        // Plugin doesn't support preferences - show message in proper container
        Scaffold(
            topBar = {
                AapsTopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(app.aaps.core.ui.R.string.back))
                        }
                    }
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(app.aaps.core.ui.R.string.plugin_no_preferences),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        return
    }

    val sectionState = rememberPreferenceSectionState()

    // For single plugin view, start with the main section expanded
    LaunchedEffect(screen.key) {
        sectionState.toggle("${screen.key}_main", SectionLevel.TOP_LEVEL)
    }

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
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val listState = rememberLazyListState()

        // Provide visibility context via CompositionLocal (same as AllPreferencesScreen)
        CompositionLocalProvider(
            LocalVisibilityContext provides visibilityContext
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScrollIndicators(listState),
                state = listState
            ) {
                // Use the same addPreferenceContent() as AllPreferencesScreen
                // This renders as collapsible sections, not navigation
                addPreferenceContent(
                    content = screen,
                    sectionState = sectionState
                )
            }
        }
    }
}
