---
name: aps-loop-move
description: plugins/aps Loop feature (screen + ViewModel + ComposeContent) is in commonMain; the module needs no build file change and no expect/actual
metadata:
  type: project
---

The Loop feature - `LoopViewModel.kt` (holds `LoopUiState`), `LoopScreen.kt`, `LoopComposeContent.kt`
- moved to `plugins/aps/src/commonMain/kotlin/app/aaps/plugins/aps/loop/compose/` and compiles for
iosArm64. Nothing was left behind and no `expect`/`actual` was needed.

**Why it was easy, for the next module:**

- **No build file edit.** `:core:ui` exports the whole Compose stack with `api(libs.cmp.*)`, so a
  module with `implementation(project(":core:ui"))` already has material3, foundation and
  `jetbrains.lifecycle.viewmodel.compose` on its commonMain classpath. `plugins/aps/build.gradle.kts`
  lists only `libs.cmp.runtime` and still compiled - do not add cmp dependencies reflexively.
- `PullToRefreshBox` and `collectAsStateWithLifecycle` both cross unchanged.
- `LoopComposeContent` was *not* pinned by any view model factory: it builds its view model with a
  plain `remember { }` over a `rememberCoroutineScope()`. Check for that shape before assuming a
  ComposeContent is blocked - `OpenAPSComposeContent` in the same module is the template.
- The only caller, `LoopPlugin`, needed **no change**: its `rh` is a `ResourceHelper`, which is a
  `TextResolver`, so widening the parameter is source compatible.

**A string trap specific to this module.** `constraints` is declared in *both*
`plugins/aps/.../res/values/strings.xml` and `core/ui/.../res/values/strings.xml`. Android resource
merging gives the local module priority, so `R.string.constraints` inside `:plugins:aps` was always
the aps one - `ApsStrings.constraints` is the faithful replacement, not `CoreUiStrings.constraints`.
The English text is identical, so picking wrong would only show up as a wrong translation.

**Done and green.** The move initially left 8 tests red for harness reasons only - see
[[move-breaks-two-test-harnesses]]. The user approved four bootstrap lines (registry registration in
the two Robolectric tests, `stubTextRefResolution(rh)` in the two mock based ones); with those the
suite is 300 tests / 0 failures, and no assertion, expected value or test name was touched.

Related: [[move-breaks-two-test-harnesses]], [[metroviewmodel-is-not-a-blocker]],
[[cmp-what-crosses-unchanged]]
