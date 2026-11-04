---
name: cmp-what-crosses-unchanged
description: Compose APIs confirmed by the iOS compiler to move from androidMain to commonMain with no change, in this repo's CMP setup
metadata:
  type: reference
---

Verified empirically by `:plugins:constraints:compileKotlinIosArm64` on a 713-line screen — all of
these crossed with **zero** edits, no expect/actual, no adapter:

- `androidx.compose.material.icons.*` including **extended** icons
  (`RestartAlt`, `School`, `ExpandMore`, `ChevronRight`, `AutoMirrored.Filled.OpenInNew`) —
  not just the small core icon set.
- `androidx.compose.ui.platform.LocalUriHandler` (`openUri`).
- `androidx.compose.runtime.saveable.rememberSaveable`.
- `androidx.compose.foundation.Canvas` with `drawLine` / `drawCircle` / `dp.toPx()` / `StrokeCap`.
- `AnimatedVisibility`, `expandVertically`, `shrinkVertically`, `animateContentSize`.
- `LazyColumn` / `itemsIndexed` / `rememberLazyListState` / `animateScrollToItem`.
- `material3` Button, OutlinedCard, Switch, LinearProgressIndicator, HorizontalDivider.

So an import survey badly overstates the work. In practice the blockers are the small number of
genuinely Android types: `LocalContext`, `Context` in a signature, and the ViewModel factory
lookup. Grep for those specifically rather than for `androidx.compose`.

Confirm the compile was real, not a no-op: check the log for a bare
`> Task :<module>:compileKotlinIosArm64` (no `UP-TO-DATE` / `NO-SOURCE`) and that the class appears
under `build/classes/kotlin/iosArm64/main/klib/<name>/default/linkdata/package_<your.package>`.
