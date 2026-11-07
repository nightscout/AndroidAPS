---
name: move-breaks-two-test-harnesses
description: Moving a screen+ViewModel to commonMain always breaks two kinds of existing test in the same two ways - budget for both before starting
metadata:
  type: reference
---

The move itself is mechanical. What is *not* obvious is that it reliably turns two classes of
existing, passing test red, for reasons that have nothing to do with the screen's behaviour. Expect
both, and check for them **before** promising "tests pass unchanged".

**1. Robolectric Compose tests go red because nobody registered the string owner.**
`app.aaps.core.ui.compose.stringResource(TextRef.Named)` resolves through `TextRefIdRegistry`, which
is populated by `MainApp` / `BaseTestApp` - neither of which runs in a module's `androidHostTest`.
So every label renders as its **raw name** and the assertion reads
`The component with Text ... contains 'Last run' ... is not displayed!`.
Fix is one line in `@Before`, and there is precedent in `ObjectivesScreenTest`:
`TextRefIdRegistry.register("<owner>") { name -> <Owner>StringIds.idOf(name) }`.
Note a test that only asserts *absence* stays green, so the failure count understates the problem.

**2. Tests holding a mocked `ResourceHelper` go red because the ViewModel switched overload.**
`ResourceHelper` is androidMain-only, so the ViewModel must take `TextResolver`, and
`rh.gs(R.string.x)` becomes `rh.gs(SomeStrings.x)`. A Mockito mock stubbed on `gs(Int)` answers the
`gs(TextRef)` form with `""` or `null` - the null then NPEs in the state data class ctor, pointing at
`LoopUiState.<init>` and looking like a production bug. Fix is `stubTextRefResolution(rh)` from
`:shared:tests` right after the mock exists; it routes the TextRef form back to the `gs(Int)` stubs
the test already writes.

**Write the safety-net test so it is immune to both.** A hand written fake that answers the id form
and the `TextRef` form with the *same* word survives the move untouched and pins the produced text
rather than the overload - see `LoopViewModelFormattingTest.FakeTextResources`. That is what lets you
prove the strings did not change.

Related: [[cmp-what-crosses-unchanged]], [[aps-loop-move]]
