# Multiplatform DI: Koin vs kotlin-inject vs Metro

Three spike branches, each converting the same modules and each verified running on a Pixel 9a with a
virtual pump. Everything below was built and measured, not read off a website.

| branch | commit | modules converted |
|---|---|---|
| `koin-spike` | `4dc3dd2cd7` | 7 |
| `kotlin-inject-spike` | `1272b26ebd` | 4 |
| `metro-spike` | `a02c00aca6` | 4 |

## Why any of this

Dagger cannot put dependency wiring in a multiplatform module. It emits Java, and AGP's multiplatform
library target has no Java compilation step, so an annotation there is processed into **nothing** and
the build still succeeds. That is why every converted module's wiring currently sits in `:app`, which
is `com.android.application` and can never be a KMP library - wiring iOS can never read.

All three candidates solve that. The question is what else they cost.

## The finding that applies to all three

Every branch hit the **same StackOverflowError on device**, in the same place.

The bridge that lets Dagger and the new framework coexist makes them mutually reachable. Asking the
new graph for an object resolves its dependencies from Dagger; Dagger reaches back - `Loop` leads to
the plugin list, which asks the new graph. Neither framework can see the other's graph, so **neither
can detect the cycle**, and compile-time checking does not help.

The fix is always the same: **the bridge must defer, never resolve eagerly.** How you spell that
differs - Koin's `single { provider.get() }` is deferred for free, kotlin-inject takes `() -> T`, and
Metro needs an ordinary wrapper class because it treats a parameterless function type as its own
provider type and rejects it as a factory parameter.

Whatever is chosen, budget for this. It cost three debugging sessions.

## Head to head

| | Koin | kotlin-inject | Metro |
|---|---|---|---|
| Mechanism | runtime resolution | KSP → Kotlin source | Kotlin compiler plugin → IR |
| Wiring in commonMain, compiles `iosArm64` | ✅ | ✅ | ✅ |
| Runs on device | ✅ 7 modules | ✅ 4 modules | ✅ 4 modules |
| **Missing dependency** | **runtime crash** | build error | build error, best diagnostic |
| Multibindings | none - invented `PluginRegistration` + literals | `Pair<Int, PluginBase>` | native `@IntoMap @IntKey`, same shape as Dagger |
| ViewModel (77 `@HiltViewModel`) | ✅ rendered on device | ✅ compiles for iOS | ✅ compiles for iOS |
| Assisted injection | ✅ `parametersOf`, **unchecked** | ✅ function type, checked | ✅ `@AssistedInject`, checked, matched **by name** |
| Cycles | ✅ lambda | ✅ lambda | ✅ `Provider<T>` |
| Singletons | default | must be asked for | must be asked for |
| Codegen | none | KSP, ~100 lines/module | **none** |
| Keeps KSP in the build | no | yes | no |
| DI tests needed per module | ~155 lines | 0 | 0 |
| Wiring size (same module) | 43 lines | 60 lines | 62 lines |
| Shape vs existing Dagger | rethink each module | close | **closest** |

### Active development, as of 2026-08-23

| | latest | last published | releases on Maven |
|---|---|---|---|
| Koin | 4.2.2 | 2026-06-15 (~2 months) | 90 |
| kotlin-inject | 0.9.0 | **2026-01-07 (~7.5 months)** | 23 |
| Metro | 1.4.2 | **2026-08-14 (9 days)** | 93 |

Koin has commercial backing (Kotzilla) and a published LTS policy - 3.5 LTS extended through June 2026.
Metro is the most actively released of the three. kotlin-inject is the quietest by a wide margin, is
still pre-1.0, and is essentially a single-maintainer project.

Adoption runs the other way: Koin ~10k GitHub stars, kotlin-inject ~1.5k, Metro fewer than either.
Note that Maven "usages" counts are useless here - apps are not published artifacts.

## What each is actually good at

**Koin.** Simplest to write, no build-step cost at all, by far the best-trodden path, and the only one
whose singletons are safe by default. Its weakness is the one that matters most here: a missing or
mis-typed binding is a crash, not a build failure. `verify()` recovers much of that statically, but it
has real blind spots (anything a lambda supplies itself needs whitelisting, and every whitelist entry
is a hole) and it needs two tests per module plus an application-level composition test. Three launch
crashes reached the device during that spike, all past a green build.

**kotlin-inject.** Compile-time checked and Dagger-shaped, so the migration is close to mechanical.
But it keeps KSP (no build-time win), its unscoped default silently produced non-singleton plugins
until caught, and the project has not published in over seven months while sitting at 0.9.0. For an app
that must be maintainable for years by volunteers, that last point is hard to ignore.

**Metro.** On the technical axes it takes the union: compile-time checking with the best error messages
of the three, real `@IntKey` multibindings identical in shape to the Dagger being replaced, and **no
annotation processing at all** - so it keeps the build-time benefit that was Koin's main advantage over
kotlin-inject. It is also the most actively developed. Against it: least adopted, and being a Kotlin
compiler plugin it is **pinned to the Kotlin version** - a Kotlin upgrade is blocked until Metro ships
a matching build. For a project that tracks Kotlin closely, that is a real operational dependency and
the main thing to weigh.

## Practical notes worth keeping

- **Source set decides.** Android-only classes (`LoopPlugin`, autotune, `CryptoUtil`, all Workers) stay
  on Dagger. Converting them buys nothing for iOS and only lengthens the bridge. Ignoring this crashed
  the app once.
- **Dagger delegates, never constructs.** Where Dagger consumers remain for a migrated type, the
  `@Provides` must call into the new graph. Building on both sides gives two instances - two
  `RunningModeReconciler` objects, each with its own observer issuing pump commands.
- **Assisted injection is a 3-case problem, not 138.** Of 34 files using `@AssistedInject`, 30 are
  Workers and stay on Dagger. The real cases are `GraphViewModel`, `ChipsViewModel` and
  `OverviewDataCacheImpl` in `:ui` - two of them view models, so on the shared-UI path.
- **Metro's Gradle plugin must be applied after the Kotlin plugin**, and also in `:app`, because
  `createGraphFactory` is a compiler intrinsic rather than a library call.
- **A Koin module must be a `fun`, never a top-level `val`** - Koin caches singles inside the `Module`
  object, so a shared val leaks instances between Koin instances.

## Scale

2,816 Dagger annotations. Pumps (1,142) and `:wear` (210) are Android-only forever and should never
move, leaving ~1,100. Of that, ~259 in `app/src/main` is wiring for already-converted modules - the
pile iOS can never read, which grows with every module converted while this stays undecided.

## Not tested for any of them

- `@IntoSet` (12 uses), `@HiltWorker` (42, Android-only anyway).
- `:ui` - 35 of the 77 view models, and all three real assisted cases.
- Anything actually **running** on iOS. All three compile for `iosArm64`; none has executed there,
  because there is no iOS app yet.
- Build-time effect of removing Dagger: measured only as potential - KSP totals 128.7s of task time in
  a full `:app` build, of which Room is 17.8s, pumps 61.9s and non-pump Dagger 49.0s. Only that 49s is
  recoverable, and only once the last non-pump Dagger annotation is gone.

## Decision

**Metro, and the target end state is Dagger fully removed - not Metro alongside Dagger.**

Taken after the three spikes above. The reasoning for going all the way rather than stopping at
"Metro for multiplatform modules, Dagger for the rest":

The expensive part of this migration is not converting modules - it is the **bridge**. Every hazard
that cost real debugging time on all three branches comes from Dagger and the new framework coexisting:
the re-entrancy StackOverflowError (three times), double instantiation, singleton identity, two mental
models. None of that is detectable by either framework, because the cycle crosses a boundary neither
can see. It ends only when Dagger does. A permanently half-migrated tree keeps every one of those costs
forever, which is the worst outcome available.

Metro's Kotlin tracking, measured, is what makes committing defensible:

| Kotlin release | date | Metro support | lag |
|---|---|---|---|
| 2.3.21 | 2026-04-23 | 1.0.0-RC4, 2026-04-24 | 1 day |
| 2.4.0 | 2026-06-03 | 1.2.0, 2026-06-10 | 7 days |
| 2.4.10 | 2026-07-14 | 1.3.1 "Test Kotlin 2.4.10-RC2", 2026-07-11 | ahead of release |

Metro 1.4.2 (2026-08-14) already states "Support Kotlin 2.5.0-dev-3513" and "Test Kotlin 2.4.20-RC" -
two versions ahead of this repo. They test against Kotlin RCs before stable ships, which is why the lag
is days rather than weeks. The compiler-plugin coupling is real but actively managed.

The accepted risks: Metro is the least adopted of the three, and the Kotlin coupling is structural even
though it is currently well handled. The mitigation is that the *shapes* are portable - the
source-set rule, the deferring-bridge rule, and a plain-data plugin registration are all
framework-neutral, so a forced move later would be a rewrite of module wiring, not of architecture.

### The Android entry points - proven

Removing Dagger means removing **Hilt**, and Hilt is what answers the Android entry points. This was
the precondition for the decision above, so it was tested first, before any volume work. All four
categories now run on Metro on a Pixel 9a.

| Hilt today | count | Metro replacement | proven by |
|---|---|---|---|
| `@HiltWorker` | 42 | `@AssistedInject` + `@AssistedFactory` into a `@ClassKey` map, read by our own `WorkerFactory` | `KeepAliveWorker`, `RunningModeExpiryWorker` |
| `@ContributesAndroidInjector` | 294 | `MembersInjector<T>` in a `@ClassKey` map, reached through `MetroMemberInjector` on the `Application` | `NetworkChangeReceiver`, `ChargingStateReceiver` |
| `@AndroidEntryPoint` | 3 | the same `MembersInjector` map | `OHLoginActivity` |
| `@HiltViewModel` | 77 | `MetroViewModelFactory` (a `ViewModelProvider.Factory`) over a `@ClassKey` map | `OHLoginViewModel` |

The cases were picked to be awkward rather than easy. `KeepAliveWorker` takes **nineteen**
dependencies, lives in another module, and runs every fifteen minutes - and two of its dependencies,
`Loop` and `ActivePlugin`, are exactly the ones that lead back into the plugin list. `OHLoginActivity`
needed two categories at once, because `@AndroidEntryPoint` filled its fields *and* made
`by viewModels()` resolve, and one of those fields is **qualified** (`@AuthUrl String`).

Evidence: `KeepAliveWorker` ran to `SUCCESS` including `checkPump()`, which needs the command queue,
active plugin and profile function. `NetworkChangeReceiver` logged its network status through its
injected logger with dagger.android's base class removed. `OHLoginActivity` rendered its welcome
screen, which reads all three injected fields and the view model's state flow.

**Coexistence works at module level, not just app level.** `:implementation` runs Metro beside Dagger
and `:plugins:sync` runs Metro beside Hilt, each with one class converted and the rest untouched. Every
lookup falls back - Metro first, then Hilt, then WorkManager's default factory - so the 294 and the 42
can move a few at a time instead of in one commit.

What this cost in mechanism, per converted class: one `@Provides @IntoMap @ClassKey` line, the same
order of work as the `@ContributesAndroidInjector` line it replaces.

### What the entry-point work taught

- **Metro will not build a `MembersInjector<T>` for a class whose fields use `javax.inject.Inject`** -
  *unless interop is switched on for the module*, which changes this picture completely. See the
  interop section below; it was found later and it matters more than anything else here.
- **`@ClassKey` produces `KClass<*>` keys**, not `KClass<out Base>`. Declaring the narrower map type
  fails to resolve.
- **A module with `internal` qualifiers must own its bridge.** `@AuthUrl` cannot be named from `:app`,
  so the Dagger-to-Metro handover for `:plugins:sync` lives in `:plugins:sync`. This turned out to be
  the better arrangement regardless: the module exposes its maps, never its graph, and when its last
  Dagger consumer goes the bridge is deleted with it instead of leaving a stub in `:app`.
- **A graph is only as visible as what it builds.** An `internal` view model forces an `internal`
  graph, which is why the bridge exposes `Map<KClass<*>, …>` rather than the graph type.
- **`hiltViewModel()` is Android-only.** `viewModel(factory = …)` is the Compose Multiplatform shape,
  so replacing `@HiltViewModel` is not only about removing Dagger - it is the form shared iOS UI needs
  anyway. That makes the 77 the most valuable of the four categories, not the most expensive.
- **Scope device evidence to the process ID.** Three AAPS variants are installed on the test phone. The
  first worker run that looked like proof was `aapsclient2`, an older build that was never reinstalled.
  Checking `pidof` against the rebuilt package is what caught it.

### The fifth pattern: a second set of the same objects

The four categories above are all about classes **Android** constructs. The History Browser is a
different question that only turned up when someone asked: it needs its **own** `OverviewData`,
`CalculationSignals`, `OverviewDataCache` and `IobCobCalculator`, because it recalculates over a
different time range and must not write into the state the running loop is using.

Today it arranges that without DI at all - `HistoryBrowserData` took fourteen dependencies, passed most
of them straight through, and constructed the four objects by hand. That ports to any framework
unchanged, so it was never a blocker. It becomes one the moment those four types move into Metro
graphs as `@SingleIn`, because then the history window must be told not to take the app-wide instance.

`MetroScopingTest` measures what Metro actually guarantees, rather than trusting the documentation:

| | result |
|---|---|
| same graph, asked twice | same instance |
| two **root** graphs from one factory | **nothing shared** |
| extension vs its parent | parent's scoped objects shared |
| two extensions of one parent | own copy of their own scoped objects |

The second row is the important one, because it rules out the obvious shortcut. "Just build the graph
twice" would hand the history window its own logger and its own database handle - it isolates far too
much, and nothing would report it. Shared leaves have to come from outside: for now from Dagger through
`DeferredRef`, and once the leaves are Metro-owned, from a parent graph via `@GraphExtension`. That is
the fourth row, and it is exactly the Dagger subcomponent this codebase would otherwise need.

**A structural consequence for the migration plan.** The bridge currently creates *seven independent
root graphs*. That is safe only because every object they share is still Dagger's, so Dagger keeps it
single. The first time two Metro graphs both scope the same type, they will silently get one instance
each - the same failure the second row describes, arriving quietly. So as modules move, the roots have
to converge on **one** root graph with extensions hanging off it. Worth deciding early; it gets more
expensive later.

What was built: `HistoryWindowGraph` with its own `HistoryWindowScope`, the cache/calculator cycle
expressed with Metro's `Provider` instead of a hand-written lambda, and `HistoryBrowserData` reduced
from fourteen constructor dependencies to one. Two of those fourteen (`AapsSchedulers`,
`FabricPrivacy`) turned out to be unused and went with the rewrite.

Verified on device: the History Browser draws its BG, IOB/BAS and COB graphs - which are the *output*
of the isolated calculation objects - and after browsing back to a date two days earlier, the live
overview still showed current values advancing in real time (BG 10.7 from 1m ago, IOB 2.48 U, COB 13 g).
Had the window shared the loop's objects, that is where it would have shown.

### Interop: the setting that decides how big this migration is

Metro's Gradle plugin can be told to read other frameworks' annotations:

```kotlin
metro { interop { includeDagger() } }   // also includeJavax(), includeHilt(), includeAnvilForDagger()
```

This is the single most important thing found so far, because it decides whether "replace Dagger"
means *rewriting 2,816 annotations* or *moving wiring while the annotations stay put*. With interop on,
converting a class means *removing its Hilt annotation and adding one binding to a graph* - its
`javax.inject.Inject` and its qualifiers keep working. `OHViewModel` was converted exactly that way and
still declares `javax.inject.Inject` today.

**Without interop, qualifiers are silently ignored.** This corrects something stated earlier in this
document. The `@AuthUrl String` injection appeared to work only because that graph happened to contain
exactly one `String`; Metro was not honouring the qualifier at all, it was matching on type. The moment
the graph held five qualified `String`s, they collapsed into one binding and the build failed with
`DuplicateBinding`. A graph with a single binding of a type would have kept working and injected a value
the qualifier was meant to exclude - a wrong value, with no error anywhere. That is a serious enough
failure mode that **interop should be considered mandatory, not optional**, for any module that uses
qualifiers.

Interop is per module and all-or-nothing: Metro then validates *every* Dagger annotation in that
module, including in classes still owned by Dagger. Turning it on for `:plugins:sync` required:

| what Metro rejected | fix | project-wide count |
|---|---|---|
| `@Reusable` is not supported at all | change to `@Singleton` | **32 uses** |
| non-final class with injected fields | add `@HasMemberInjections` (inert for Dagger) | 1 here |
| `@Provides` with an implicit return type | write the return type | 1 here |
| injected field from a Java supertype (`DaggerAppCompatActivity`) | `includeDagger()`, not just `includeJavax()` | n/a |

Each is small and mechanical, but they are a per-module entry fee that has to be paid before any of
that module's classes can move. `@Reusable` is the one to look at first, because Metro has no
equivalent and 32 call sites have to be re-examined rather than mechanically rewritten - `@Reusable`
permits caching, `@Singleton` guarantees it, so each one is a small semantic decision.

### A non-KMP plugin, and a whole feature

The three plugins converted earlier were all `commonMain` in KMP modules, constructor-injected and
leaf-like - chosen to prove the multiplatform point, not to stress plugin wiring. Open Humans is the
opposite: an ordinary Android library module, `Context` in the constructor, Android resources, an icon,
Compose content that casts the plugin back to its own type, `PluginBaseWithPreferences` rather than
`PluginBase`, and its own `ownPreferences`.

The whole feature moved, not just the plugin: the plugin, its three preference delegates, its HTTP
client, its upload worker, two view models and its activity. `OpenHumansModule` was deleted and its five
constants now live in the graph. What still crosses from Dagger is only app-wide infrastructure -
logger, resources, preferences, context, database, notifications - which is by definition the last thing
that can move. Converting a feature end to end is worth more than half-converting several, because the
bridge is the expensive part.

**The behaviour that had to be preserved:** the Dagger binding carried a `@NotNSClient` qualifier, and
`AppModule.providesPlugins` merges that bucket only when the build is not an AAPSCLIENT one. Merging
Metro's plugins unconditionally would have added Open Humans to every follower build. The Metro side
therefore keeps a separate `notNsClientPlugins()` map, merged under the same condition. This is the
second time a plugin-bucket qualifier has nearly been dropped in this migration; the buckets deserve
attention on every module that moves.

**Verified on device:** the plugin list holds **61 entries and 61 distinct classes - no duplicates -
with `OpenHumansUploaderPlugin` appearing exactly once**, read from `ConfigBuilderImpl.loadSettings`,
which logs one line per plugin in the assembled list. That is the check that a converted plugin has not
been contributed by both frameworks. The login activity still renders, and the `aapsclient` variant
still compiles.

The plugin's own Compose screen was checked too, by enabling the plugin on the test phone. It renders
"Open Humans is currently inactive", which is `OHUiState(isLoggedIn = false)` - a value produced by
`OHViewModel` collecting `OHStateDelegate.stateFlow`. So the Metro-built view model, reached through
`viewModel(factory = …)` with no `hiltViewModel()` anywhere in the path, received the Metro-built
delegate and drove the UI. Nothing is uploaded without an OAuth token, and the plugin was switched back
off afterwards.

That makes the whole slice device-verified end to end: plugin in the list, plugin screen, view model,
delegates, login activity, qualified injection.

### What is still not proven

- Only two of 42 workers, two of 294 injectors, one of 3 activities and one of 77 view models were
  converted. The mechanism is proven; the volume is not.
- **`SavedStateHandle` view models** (8 of them) take the assisted-factory path rather than the plain
  provider path used here. The design accounts for it - `MetroViewModelCreator` receives
  `CreationExtras` - but no such view model has been converted yet.
- `@IntoSet` (12 uses) is still untested on any of the three frameworks.
- Nothing has executed on iOS, for any framework, because there is no iOS app yet.
