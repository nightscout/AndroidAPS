---
name: constraints-objectives-move
description: State of the :plugins:constraints androidMain -> commonMain Compose move, and exactly what blocks the two files that did not move
metadata:
  type: project
---

`ObjectivesScreen.kt` (713 lines) moved to commonMain and compiles for iosArm64. The only blocker
in the whole file was a dead `android.content.Context` parameter threaded
Screen -> ViewModel -> `Objective.UITask.code`.

**Why:** the `context` was live when `UITask` was added (commit `44c5089d08`, Oct 2024) — it fed
`ToastUtils.errorToast(context, ...)` and the old `passwordCheck.queryPassword(context, ...)`.
Both consumers were later migrated to context-free APIs (`showMessage` snackbar lambda, and
`PasswordCheckHost` which is already commonMain), leaving the parameter ignored as `_` at the only
UITask in the app (Objective0 "verify_master_password"). So it is migration residue, not a lost
caller.

**How to apply:** the two files still in androidMain are blocked by different, larger things —
do not retry them as a small increment:

- `ObjectivesComposeContent.kt` needs `MetroViewModelFactoryOwner`, which only exists in
  `core/ui/src/androidMain`. Moving it needs a **shared ViewModel-factory port in `:core:ui`** —
  a cross-module design decision, not a screen move. `plugins/sync`'s `OHComposeContent.kt` has
  the identical blocker, so the port would serve at least two callers.
- `ObjectivesViewModel.kt` drags `ObjectivesPlugin` -> `Objective` + `Objective0..9` (11 files,
  all androidMain) and `objectives/SntpClient.kt`, which is real JVM/Android
  (`java.net.DatagramSocket`, `android.os.SystemClock`) and needs an expect/actual or a port.

Related: [[cmp-what-crosses-unchanged]], [[feedback-mutation-restore-proof]]

Loose end found, not touched: `commonMain/.../objectives/objectives/SntpClient.kt` is an empty
file containing only a `package` line — leftover debris from an earlier move.
