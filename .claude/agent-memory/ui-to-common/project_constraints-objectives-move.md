---
name: constraints-objectives-move
description: State of the :plugins:constraints androidMain -> commonMain Compose move, and the three exact root causes that pin the remaining 14 objectives files
metadata:
  type: project
---

`ObjectivesScreen.kt`, `ExamBottomSheet`, `LearnedBottomSheet`, `NtpProgressDialog`,
`ObjectivesUiState`, the events and the keys are already in commonMain and compile for iosArm64.
104 androidHostTest tests, 0 failures (2026-08-30).

Still in androidMain: `ObjectivesComposeContent`, `ObjectivesViewModel`, `ObjectivesPlugin`,
`SntpClient`, `Objective` + `Objective0..9`.

**Why:** measured with three probe moves in Aug 2026 (moved, compiled `:plugins:constraints:
compileKotlinIosArm64`, reverted). A raw move of the whole cluster gives 88 errors; applying only the
mechanical fixes leaves **exactly 25, and they are three root causes, one per file**:

1. `Objective.kt` - `rh.gq(app.aaps.core.ui.R.plurals.days/hours/minutes, ...)` in
   `MinimumDurationTask.getDurationText`. See [[no-plural-api-in-commonmain]].
2. `ObjectivesPlugin.kt` - the `@APS` multibinding qualifier is in
   `core/interfaces/src/androidMain/.../di/APS.kt` and carries `javax.inject.Qualifier`.
3. `SntpClient.kt` - `java.net.DatagramSocket`, `SecureRandom`, `android.os.SystemClock`.
   iOS *can* do SNTP (it is plain UDP), so this is a work-sizing question, not a capability gap.
   `ktor-network` (UDP on JVM+Native) is not in the version catalog; only the ktor *client* is.

Everything else in the cluster is cascade. `ObjectivesComposeContent` and `Objective0..9` produced
**zero** errors of their own - they are pinned only by transitive references, so they cannot move
before 1-3 are settled. There is **no safe movable subset**; a run that tries one will move nothing.

**How to apply:** do not reopen this as "a screen move". It is three separate decisions, and 2 and 3
are `:core:*` / new-library questions. Verified mechanical, if the cluster ever does move:
`ResourceHelper` -> `TextResolver` everywhere (`PluginBase.rh` is already `TextResolver`, so
`ObjectivesPlugin`'s `override val rh: ResourceHelper` is a narrowing accident);
`import kotlin.jvm.JvmSuppressWildcards` added explicitly; the unused
`import app.aaps.plugins.constraints.R` in `Objective.kt` deleted; `System.currentTimeMillis()` ->
`dateUtil.now()` (a behaviour change under a faked clock - name it).

Superseded: an earlier note here claimed `ObjectivesComposeContent` was blocked by
`MetroViewModelFactoryOwner`. It is not - see [[metroviewmodel-is-not-a-blocker]].

Related: [[cmp-what-crosses-unchanged]], [[feedback-mutation-restore-proof]],
[[move-breaks-two-test-harnesses]]
