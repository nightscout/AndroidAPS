package app.aaps.plugins.source.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.core.ui.compose.AapsTopAppBar
import app.aaps.core.ui.compose.stringResource
import app.aaps.plugins.source.SourceStrings

/**
 * Which job the navigation icon of a native CGM screen does — see the convention documented on
 * [app.aaps.core.ui.compose.AapsTopAppBar].
 */
enum class CgmNavIcon {

    /** Pure navigation (status, warm-up, log): back arrow. */
    Back,

    /** A task the user either commits or abandons (sensor start): close cross. */
    Close,
}

/**
 * Shared frame for every native CGM driver screen.
 *
 * Three things are settled here once, instead of being re-decided per screen:
 *
 * - **The screen scrolls.** Content is given by [content] and the screens put a single scrolling
 *   container inside it. The original screens used a plain non-scrolling `Column`, so in landscape,
 *   on a tablet (where the theme scales type by 1.5) or with a staging sensor present, whatever sat
 *   at the bottom — including the promote button — was drawn past the edge and could not be reached.
 * - **Running text stops at a readable width.** On a wide window the content column is centred at
 *   [CgmSpacing.readableContentMaxWidth] instead of stretching to the screen edge. Pass
 *   `constrainWidth = false` for a screen that lays out its own panes.
 * - **The bar is the same everywhere**, with the navigation icon picked by screen role.
 */
@Composable
fun CgmScaffold(
    title: String,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
    navIcon: CgmNavIcon = CgmNavIcon.Back,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    constrainWidth: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            AapsTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onNavigate) {
                        when (navIcon) {
                            CgmNavIcon.Back  -> Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(SourceStrings.cgm_nav_back),
                            )

                            CgmNavIcon.Close -> Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = stringResource(SourceStrings.cgm_nav_close),
                            )
                        }
                    }
                },
                actions = actions,
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .then(
                        if (constrainWidth) {
                            Modifier.widthIn(max = CgmSpacing.readableContentMaxWidth)
                        } else {
                            Modifier
                        },
                    )
                    .fillMaxSize(),
                content = content,
            )
        }
    }
}

/**
 * The one scrolling container of a screen (or of one pane of it).
 *
 * Every list of unknown length — scanned sensors, log lines — belongs here as `items()`. A list with
 * its own bounded height nested inside another scroll was what pinned the sensor list of the start
 * screen to 216 dp whatever the screen could offer.
 */
@Composable
fun CgmLazyColumn(
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(AapsSpacing.extraLarge),
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
        content = content,
    )
}
