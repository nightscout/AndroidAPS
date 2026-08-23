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

### What must be proven before the volume work

Removing Dagger means removing **Hilt**, and Hilt is what currently answers the Android entry points:

| | count | who answers it today |
|---|---|---|
| `@ContributesAndroidInjector` | 294 | dagger.android |
| `@HiltWorker` | 42 | Hilt + WorkManager integration |
| `@AndroidEntryPoint` | 3 | Hilt |
| `@HiltViewModel` | 77 | Hilt |

These are the precondition. Converting more multiplatform modules proves nothing about them, and if
Metro cannot replace them cleanly the end state falls back to "Metro for multiplatform modules, Dagger
for the rest" - which is the outcome this decision exists to avoid. Prove the entry points first.
