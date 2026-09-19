---
name: probe-move-three-stage
description: How to size a blocked feature move accurately - three probe compiles (raw single file, raw cluster, cluster plus mechanical fixes), each moved and reverted in one Bash call
metadata:
  type: reference
---

To find the *real* root causes of a blocked move, do not grep imports and do not stop at the first
raw compile. Run three probes, each as a single Bash call that moves, compiles and moves back before
returning:

1. **The one file alone.** Tells you which of its dependencies are androidMain, separated from
   everything else. Cheap and unambiguous.
2. **The whole cluster raw.** Shows every blocker at once, but the count is misleading - one
   unresolved type produces dozens of cascade errors.
3. **The cluster with only the mechanical fixes applied** (`ResourceHelper` -> `TextResolver`,
   missing `kotlin.jvm.*` imports, `System.currentTimeMillis()` -> `dateUtil.now()`, dead imports).
   **This is the probe that actually answers the question.** What survives it is the true blocker
   set, one entry per real problem.

On `:plugins:constraints/objectives` this went 88 errors -> 25, and the 25 were three root causes.
Reporting the 88 would have been useless; reporting the 3 is a plan.

**Why:** an import grep both over- and under-reports. It flagged `androidx.compose` (fine) and
`kotlinx.coroutines.Runnable` (fine), and it missed that `@JvmSuppressWildcards` fails in commonMain
only because the implicit `kotlin.jvm.*` import is JVM-only - adding
`import kotlin.jvm.JvmSuppressWildcards` fixes it, precedent in
`database/impl/src/commonMain/.../UserEntry.kt`, a module that does build for iosArm64.

**Move and restore FILE BY FILE, never directory by directory.** `mv androidMain/x/compose
commonMain/x/compose` when the target directory **already exists** does not replace it - it nests the
source inside as `commonMain/x/compose/compose`. The restore step `rm -rf commonMain/x/compose` then
deletes the pre-existing commonMain files too. This happened on 2026-08-30 probing `:ui` and wiped
**106 tracked commonMain files**; they came back only because git status showed them all as plain
` D` with no ` M`, so `xargs git checkout --` on that exact file list was safe. Use an explicit
`FILES="a.kt b.kt"` list, `cp` each to the backup, `mv` each, then `rm -f` each and `cp` each back.

**How to apply:** back each file up with `cp` to the scratchpad first, restore with
`rm -f` + `cp` from that backup, and finish the same call with
`git -C E:/GitHub/AndroidAPS status --porcelain -- <module>` so the proof of revert is in the output.
A probe that also edits a `build.gradle.kts` must back that file up and `cp` it back too.
Watch that output for files you did not touch: on 2026-08-30 a probe run surfaced an edit to
`plugins/aps/.../LoopViewModel.kt` made by a concurrent process, and only the empty-status habit
caught it. Never `git checkout .` to clean up - it would have swallowed that foreign work.

Related: [[feedback-mutation-restore-proof]], [[constraints-objectives-move]]
