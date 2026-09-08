package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.interfaces.resources.TextRefValueRegistry
import app.aaps.core.interfaces.resources.formatTemplate
import app.aaps.core.keys.interfaces.TextRef

/**
 * Reads text from the generated English string maps.
 *
 * This was a placeholder that answered with the string's **name**, so a settings screen read
 * `configbuilder_general` instead of "General". It is real now: `GenerateKeyStringsTask` emits a
 * `name -> text` map per module, and `IosStringOwners` registers every module the iOS framework
 * links before the first screen is composed. A name with no entry falls back to the name itself,
 * which is what an unregistered owner looks like - visible on screen rather than blank.
 *
 * **Arguments are not substituted yet, so a format string still shows its placeholders.** The
 * substitution the app uses lives in `formatTemplate`, which is `internal` to `:implementation`,
 * and this file is in `:core:ui` - below it in the dependency graph, so it cannot be called from
 * here. `GeneratedTextResolver` has the same text and does format, so the non-Compose path is
 * already correct; only this one is short. Moving `formatTemplate` beside `TextRefValueRegistry` in
 * `:core:interfaces` would close it for both, and is written up in `_docs/ios_blockers.md`.
 *
 * [TextRef.AndroidRes] carries a number that means nothing off Android, so it stays unanswerable
 * here - shared code producing one is the bug it points at.
 */
@Composable
actual fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.Named      -> formatTemplate(TextRefValueRegistry.textOf(ref) ?: ref.name, ref.args)
    is TextRef.AndroidRes -> "?"
}
