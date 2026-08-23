# Metro migration: what is done, and what needs a decision

Companion to `DI_FRAMEWORK_COMPARISON.md`, which holds the reasoning. This file is the working state.

## Done and on the branch

Branch `metro-spike`. Four commits, each built and tested; the app was launched on the Pixel after
every one of them.

| commit | what |
|---|---|
| `0488e94e69` | the four Android entry points, history window graph, whole Open Humans feature |
| `0a91a58c7a` | `BTReceiver`, `SceneExpiryWorker`, removal of dead dagger.android bindings |
| `422ea08e55` | `@Reusable` sweep, plugin-list merge guard |
| `f60eb50abf` | Dagger interop on `:plugins:configuration` and `:plugins:automation` |

Device state after the last commit: 61 plugins in the list, 61 distinct classes, no duplicates, no
crash, and the new merge guard reports nothing wrong.

## Decisions waiting for you

### 1. Four `@Reusable` files were deliberately not converted

22 of the 26 were swept to `@Singleton`. These four were left because each has a side effect at
construction time, so making the instance permanent changes behaviour rather than just sharing it:

| file | why it was left |
|---|---|
| `implementation/.../resources/ResourceHelperImpl.kt` | its `init` snapshots `localizedContext` from the language preference and only rebuilds on one key. As a guaranteed singleton, a stale locale becomes permanent. 206 files import `ResourceHelper`. |
| `implementation/.../utils/fabric/FabricPrivacyImpl.kt` | its `init` is the only production code that pushes `MaintenanceEnableFabric` into Firebase. Pinning it freezes the collection flag until restart. Better fix: move those calls to a preference observer. |
| `implementation/.../maintenance/ImportExportPrefsImpl.kt` | `pendingExportFile` is a handshake between `prepareExport()` and `executeExport()`. `@Singleton` is *more* correct than today, but it makes the slot global, so two overlapping exports would share it. Better fix: return the handle in `ExportPreparation`. |
| `implementation/.../protection/ProtectionCheckImpl.kt` | holds the app-wide unlock session and two `MutableStateFlow`s whose producers and consumers are different injection sites. `@Reusable` here is a latent bug and `@Singleton` is a **fix** - but it changes how a security feature behaves, so it should be your call, not an overnight sweep. |

`DexcomTirImpl` was a fifth case and is already handled: its `@Reusable` was dead metadata (the class
is built with a plain `DexcomTirImpl()`, never injected) **and** `@Singleton` would have been a real
bug, because it is a per-calculation accumulator with no reset - shared, it would add each new 14-day
window on top of the last and produce wrong but plausible Stats numbers. The annotation was deleted.

One `@Reusable` remains in `pump/omnipod/dash`, left alone because pump drivers move last.

### 2. Which module to convert next

Two modules already have interop on and cost nothing to flip. The survey ranks the rest:

| rank | module | fee | the numbers that matter |
|---|---|---|---|
| done | `plugins/configuration` | none | flipped, compiled first try |
| done | `plugins/automation` | none | flipped, compiled first try, includes a Dagger `@IntoSet` |
| 1 | `plugins/constraints` | LOW | qualified `Map` multibinding, 10 `@Binds @IntoMap`, nested qualifier - first real test of qualifiers under interop |
| 2 | `plugins/source` | LOW | 12 `@HiltWorker`, 2 `@ContributesAndroidInjector` - first module that needs the worker seam at scale |
| 3 | `implementation` | MEDIUM | biggest, but half-proven already: both seams were built and device-tested in this module |
| last | `ui` | looks LOW, is not | 35 `@HiltViewModel`, 2 `DaggerAppCompatActivity`, 6 `@ContributesAndroidInjector`, 3 assisted pairs, a Hilt `@EntryPoint` with 4 `EntryPointAccessors` call sites, and 4 widgets that hand-roll `(context.applicationContext as HasAndroidInjector).androidInjector().inject(this)` |

`ui` is the one to be careful with. Hilt `@EntryPoint` and that hand-rolled injector call are not
`dagger.*` annotations, so `includeDagger()` makes no promise about them, and anything that breaks
there is immediately visible to users.

### 3. The structural one: seven root graphs

The bridge currently creates seven independent Metro root graphs. That is safe **only** because every
object they share is still Dagger's, so Dagger keeps it single. The first time two Metro graphs both
scope the same type, they will silently get one instance each - proven in `MetroScopingTest`, where two
root graphs from the same factory share nothing at all.

So as modules move, the roots have to converge on **one** root graph with `@GraphExtension`s hanging
off it. This gets more expensive the longer it waits. It is the biggest open design decision.

## Notes for whoever continues

- `TimeDateOrTZChangeReceiver` is the last dagger.android receiver in `:implementation`. It was left
  on purpose: it has `@Inject @ApplicationScope lateinit var appScope: CoroutineScope`, a **qualified**
  field. Converting it before interop is on for that module would silently ignore the qualifier and
  match on type - the exact failure described in the comparison doc. Turn interop on first.
- Metro matches assisted parameters **by name**. Every worker converted to `MetroWorkerCreator` must
  name its constructor parameters `context` and `params`, whatever they were called before.
- The plugin list merge is now checked at startup (`mergePlugins`, `PluginListMergeTest`). If a plugin
  is ever contributed by both frameworks, or two sources fight over one order key, the log says so with
  `PLUGIN LIST:`. Grep for that after any conversion.
