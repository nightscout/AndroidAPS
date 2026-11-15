---
name: metroviewmodel-is-not-a-blocker
description: MetroViewModelFactoryOwner is NOT a commonMain blocker - the metroViewModel() helper has an expect/actual with a working iOS actual and is used in 37 files
metadata:
  type: reference
---

**Do not treat a ViewModel factory lookup as a reason a ComposeContent cannot move.** Check
`core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/MetroViewModel.kt` first.

**Why:** `metroViewModel()` is a commonMain `expect` with a **working iOS `actual`**, and about 37
files already use it. Two earlier runs of this migration recorded `MetroViewModelFactoryOwner` as a
blocker and stopped on it; that was wrong both times. What those files actually had was the *old
inline cast* - `viewModel(factory = (LocalContext.current.applicationContext as
MetroViewModelFactoryOwner).metroViewModelFactory)` - which does need `LocalContext` and so is
Android only. The fix is to call the helper, not to leave the screen behind. The user cleared the
last two stragglers (`ObjectivesComposeContent`, `OHComposeContent`) in August 2026.

**How to apply:** when a ComposeContent looks pinned by the factory, grep it for `LocalContext` /
`MetroViewModelFactoryOwner`. If it has the inline cast, swapping to `metroViewModel()` is the move.
If it has neither - like `LoopComposeContent`, which builds its view model with a plain
`remember { }` over `rememberCoroutineScope()` - it was never blocked at all.

Related: [[aps-loop-move]], [[cmp-what-crosses-unchanged]]
