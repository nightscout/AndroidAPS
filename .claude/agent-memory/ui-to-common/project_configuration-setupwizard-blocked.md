---
name: configuration-setupwizard-blocked
description: The last 3 androidMain Compose files in :plugins:configuration cannot move; SWDefinition and CryptoUtil pin them, and both need a :core:* decision
metadata:
  type: project
---

`:plugins:configuration` has 3 Compose files left in androidMain. **None of them can move without a
`:core:*` change.** Verified by `:plugins:configuration:compileKotlinIosArm64`, not by grep.

- `SWEditEncryptedPassword.kt` - one root cause: `app.aaps.core.objects.crypto.CryptoUtil`
  (`:core:objects/androidMain`). Everything else in the file crossed.
- `SWPermissions.kt` - one root cause: `SWDefinition` (androidMain). All 14 other errors cascade
  from it. Note `androidx.lifecycle.Lifecycle` / `LifecycleEventObserver` /
  `androidx.lifecycle.compose.LocalLifecycleOwner` **all resolved** via the JetBrains republish.
- `SetupWizardScreen.kt` - `SWDefinition`, plus two mechanical ones (`BackHandler`, `Dispatchers.IO`).

`SWDefinition.kt` (androidMain, not a Compose file) is the hub. Its blockers:

| Blocker | Where | Kind |
|---|---|---|
| `pump.comment` import | in-module | **stray unused import** - `.comment(TextRef)` is `SWItem.comment`, not the `PumpEnactResult` extension |
| `ResourceHelper` | in-module | mechanical: `TextResolver` has `gs(TextRef)`; used once, `rh.gs(...)` |
| `AapsSchedulers` | in-module | **dead constructor param** - declared, never used (RxJava residue) |
| `ApplicationScope` | `:core:interfaces` | uses `javax.inject.Qualifier`; needs `dev.zacsweers.metro.Qualifier` |
| `FileListProvider` | `:core:interfaces` | genuine: `androidx.documentfile.provider.DocumentFile` + `java.io.File` |
| `CryptoUtil` | `:core:objects` | genuine: JCE + spongycastle, and it defines the stored master-password hash format `hmac:<salt>:<hmac>` |

**Why it matters:** `CryptoUtil` is the hard one. It is not just an API to port - it fixes a format
already written to every user's preferences, so a multiplatform reimplementation needs agreed golden
vectors before anyone writes code.

**How to apply:** do not try to move these as a "small increment". Do not decouple `SWPermissions`
from `SWDefinition` just to get one file across - that leaves the setup wizard split over two source
sets, which reads as done and is not. Ask about `CryptoUtil` and `FileListProvider` first.

Covered before the move (11 tests, androidHostTest, all proven to fail under mutation):
`SWEditEncryptedPasswordTest` (5), `SWPermissionsTest` (6). `SetupWizardScreen`'s next/previous
page-skipping is **untested** - the logic is local functions inside the composable and testing it
needs a real `SWDefinition` (25 injected deps).

Related: [[constraints-objectives-move]], [[cmp-what-crosses-unchanged]],
[[feedback-mutation-restore-proof]]
