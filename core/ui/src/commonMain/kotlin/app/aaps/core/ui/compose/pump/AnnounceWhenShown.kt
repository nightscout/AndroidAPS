package app.aaps.core.ui.compose.pump

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics

/**
 * Makes a screen reader say [text] when this element appears, and again if [text] changes.
 *
 * For the status line of a wizard state: "Priming, please wait", "Priming done, press Next", "Enter
 * the PIN shown on the pump". A wizard swaps those in and out as the pump works, and a blind user
 * otherwise hears nothing - not when priming ends, not when a Next button appears, not when the pump
 * waits for a PIN. A live region does not help, because Compose skips live-region events for a node
 * that has just appeared; a pane title is the supported way (see `Banner.kt`).
 *
 * Put it on ONE status text per state, not on a progress bar or a value that changes every second,
 * or the user hears it again on every change.
 *
 * Never inside a container that merges its descendants (`clickable`, `selectable`, `toggleable`,
 * `Card(onClick)`, `ListItem`, `semantics(mergeDescendants = true)`): the pane-title merge policy
 * throws as soon as a screen reader builds the merged tree.
 */
fun Modifier.announceWhenShown(text: String): Modifier = semantics { paneTitle = text }
