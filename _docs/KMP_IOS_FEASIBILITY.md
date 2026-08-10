# Kotlin Multiplatform and a possible iOS client

Written 2026-08-05. Notes from a review of how far AAPS is from sharing code with an iOS app.

The goal that started this: **an iOS AAPSClient (follower), not a master app.** Not necessarily a
full KMP app, just a way to avoid writing everything again from zero.

All numbers below were measured on the `dev` branch. They are counts of files under `src/`,
build folders excluded.

---

## 1. Short answer

*(Written when there was no KMP setup at all. Three modules are multiplatform now - `:core:data`,
`:core:nssdk` and `:core:keys` - and the last of them compiles for real iOS targets. See section 7
for where things actually stand; the rest of this section is kept because the reasoning still holds.)*

There was **no KMP setup in the project** when this note was written. No module used the
multiplatform plugin. (`:pump:combov2:comboctl` has `commonMain` / `androidMain` folder names left
over from the upstream project, but it builds as a normal Android library.)

The shape of the code is much better than in a typical Android app, mostly because of the Compose
migration and the RxJava removal. The realistic path is **Kotlin Multiplatform with Compose
Multiplatform for the UI**, done step by step, starting with a small working slice on a real iPhone.

---

## 2. What is already fine

| Area                           | State                                                                                            | Why it matters                         |
|--------------------------------|--------------------------------------------------------------------------------------------------|----------------------------------------|
| `:core:data`                   | 58 files, plain `java-library`, **0** Android imports. Two `expect`/`actual` away, see section 8 | The first module to make multiplatform |
| `:core:keys`                   | 46 files, **0** Android and **0** `java.*` imports since Wave 3                                  | Only the 381 `R.string` ids block it   |
| Room                           | 46 DAOs, **0** RxJava return types, 22 `suspend`, already on `BundledSQLiteDriver`               | This is exactly the Room KMP setup     |
| Compose                        | **0** XML layouts in `:core:ui`, 2 left in `:ui`                                                 | Compose Multiplatform can use this     |
| `LocalContext.current`         | 7 in `:core:ui`, 8 in `:ui`                                                                      | Very small coupling to Android         |
| Network code                   | 5 files touch Retrofit / OkHttp, 1 touches socket.io                                             | REST part is small enough for Ktor     |
| kotlinx.serialization          | 64 files (Gson: 34)                                                                              | Already the main choice                |
| kotlinx-datetime               | Declared in `libs.versions.toml`                                                                 | Ready to use                           |
| `androidx.lifecycle` ViewModel | Multiplatform since 2.8                                                                          | Most of the 129 uses are fine          |
| Vico charts                    | Ships a `multiplatform` artifact                                                                 | Only the artifact name changes         |

The biggest surprise was `:core:ui`. Of its 434 files:

- 424 are under `compose/`
- **0** import Dagger or `javax.inject`
- 27 import `app.aaps.core.interfaces`
- 34 import `app.aaps.core.keys`
- **only 16 import `android.*`**

Wave 14 re-measured that last line, because it is the one that decides whether a shared Compose UI is
realistic, and an earlier count of "427 of 434" was wrong - it grepped `^import android` without the
dot, which also matches `androidx`. The real figure is 16. The other 424 import `androidx.*`, and
almost all of those are `androidx.compose.*`, which Compose Multiplatform publishes under the *same
package names*. What is left is a tail of about 26 non-Compose androidx imports -
`core.graphics` (6), `lifecycle.compose` (5), `annotation` (3), `activity.compose` (3), and single
uses of `fragment.app`, `appcompat.app`, `core.view`, `core.content` and `activity.result`.

So most of the Compose work of the last year can be reused.

---

## 3. What does not work on iOS

### Libraries with no Kotlin/Native version

| Library                                                | Files                                                  | Replacement                             |
|--------------------------------------------------------|--------------------------------------------------------|-----------------------------------------|
| Dagger / Hilt                                          | ~300                                                   | kotlin-inject, Metro, or Koin           |
| RxJava 3                                               | RxBus in 35 `:ui`, 19 config, 18 sync, 13 impl, 12 aps | SharedFlow                              |
| Retrofit + OkHttp                                      | 9 / 11, **0 in `:core:nssdk`**                         | Ktor client - done there, see section 8 |
| socket.io-client                                       | 1 (`NSClientV3Service`)                                | **Do not replace** - see section 3a     |
| Gson                                                   | 46, **0 in `:core:nssdk`**                             | kotlinx.serialization - see section 8   |
| `org.json` (`JSONObject`)                              | 227, **0 in `:core:nssdk`**                            | kotlinx `JsonObject` - see section 8    |
| WorkManager                                            | 44                                                     | See warning below                       |
| joda-time                                              | 5                                                      | kotlinx-datetime                        |
| `java.text.DecimalFormat`                              | 74                                                     | Done, see section 8                     |
| `java.text.SimpleDateFormat`                           | 7                                                      | kotlinx-datetime formatting             |
| `java.util.concurrent.TimeUnit`                        | 108 files, but only 93 sites convertible               | `kotlin.time.Duration`                  |
| `Executors`, `ConcurrentHashMap`                       | 14                                                     | Coroutine dispatchers, map plus mutex   |
| `java.security`, `javax.crypto`                        | ~20                                                    | cryptography-kotlin, or expect/actual   |
| `java.io.File`                                         | 12                                                     | okio                                    |
| spongycastle, tink-android                             | 2                                                      | as above                                |
| commons-lang3, Guava                                   | 4                                                      | Inline the few helpers                  |
| slf4j, logback-android                                 | 4                                                      | Kermit or Napier                        |
| kotlin-reflect                                         | 9                                                      | No reflection on Native, must go        |
| Firebase                                               | 4                                                      | GitLive Firebase KMP, or expect/actual  |
| Play Services                                          | 2                                                      | Android only by nature                  |
| androidx.glance (widgets)                              | 7                                                      | WidgetKit is Swift only, no reuse       |
| Garmin, osmdroid, androidsvg, appauth, java-otp, zxing | 1-3 each                                               | Mostly not client features              |

### Android framework

`Context` (28 impl, 28 sync, 16 source, **15 in `:core:interfaces`**), SharedPreferences,
notifications, the NSClient foreground service, DocumentFile / SAF (9 files),
Fragment / AppCompatActivity (12), and `R.string` (~136 `:ui`, 61 `:core:ui`, 60 impl, 60 sync).

### Two that are not only porting cost

- **socket.io** - not a library choice, a protocol the server dictates. See section 3a; it is the
  one entry in the table above that must be **kept and abstracted**, not replaced.
- **WorkManager** - iOS has no equivalent. `BGTaskScheduler` only gives wake ups that the system may
  delay for hours. A follower that expects to run every 5 minutes cannot work that way. The real
  answer is push (APNs) from a server, which changes the design, not only the code.

### Habits that block portability

1. **Android types in `:core:interfaces`** - 49 of 253 files import Android (`SP`,
   `ResourceHelper`, `UiInteraction`, `PluginDescription`). It is the contract module, so anything
   depending on it inherits Android.
2. **Resource ids used as data** - `@StringRes Int` inside `PluginDescription`, notifications and
   `UserEntryPresentationHelper`. An `Int` resource id means nothing off Android.
3. **`PluginBase` is an Android class** - the plugin registry is the spine of the app.
4. **Field `@Inject` into Fragments and Services** - constructor injection ports, field injection
   does not.
5. **RxBus as a global untyped bus** - the main reason logic classes cannot move.
6. **Reflection based serialization** - Gson plus kotlin-reflect.
7. **Assuming background execution is always possible** - the deepest assumption of all.

---

## 3a. socket.io - keep it, do not replace it

**Nightscout runs a Socket.IO server.** That is the reason AAPS uses a Socket.IO client, and it is
not a choice this project gets to revisit.

Socket.IO is not "WebSocket with a helper library". It is its own protocol on top: Engine.IO
framing, a handshake, namespaces, acks and its own reconnect rules. **A plain Ktor WebSocket client
cannot talk to a Socket.IO server at all.** Polling is not a fallback either - dropping the socket
means giving up push updates, which is the whole point of NSClientV3.

So this dependency is **abstracted, not removed**:

| Where   | What                                                    |
|---------|---------------------------------------------------------|
| Android | keep `io.socket:socket.io-client:2.1.2` **unchanged**   |
| iOS     | `socket.io-client-swift`                                |
| shared  | a small `expect interface` over the calls actually used |

Both clients are written by the Socket.IO project itself, so protocol compatibility follows the
server rather than a third party's reimplementation. That matters more here than saving a
dependency.

**The API surface is tiny.** `NSClientV3Service` uses only:

```
IO.socket(url) · .on(event, listener) · .off(event, listener)
.connect() · .disconnect() · .emit(..., Ack)
Socket.EVENT_CONNECT / EVENT_DISCONNECT
```

with the events `create`, `update`, `delete`, `announcement`, `alarm`, `urgent_alarm`,
`clear_alarm`. About 30 lines of `expect` / `actual` covers it.

Kotlin Multiplatform Socket.IO libraries do exist - [moko-socket-io], [KotSock] (both wrap the same
two native clients), and pure Kotlin ports such as [dyte-io/socketio-kotlin] and [kmp-socketio]. For
this API surface a third party wrapper buys about 30 lines while adding a dependency on the sync
path. A pure Kotlin port is worse: it would **replace the working Android client** with a
reimplementation, which is the wrong direction of risk for a medical app.

### Where to look first

Since the plan is to write our own small wrapper rather than depend on one, the useful reading is
the existing wrappers' source, because they already are the `expect` / `actual` we would write:

| Link                      | Why                                                                                                          |
|---------------------------|--------------------------------------------------------------------------------------------------------------|
| [moko-socket-io]          | Cleanest reference. Small, `expect class Socket` over the two native clients.                                |
| [KotSock]                 | Same approach, a second opinion on the API shape.                                                            |
| [moko-socket-io-sample]   | A working KMP app using it, by the moko maintainer. Shows the CocoaPods wiring for `socket.io-client-swift`. |
| [KMP Socket.IO deep dive] | Walks through building exactly this kind of wrapper. Closest thing to the POC we would be reproducing.       |

**Check the dates before trusting any of them as a dependency.** moko-socket-io states Gradle 6.8+,
Android API 16+, iOS 11.0+ - those baselines are from around 2021, while this project is on Gradle
9.6.1 and Kotlin 2.4.10. That is a warning sign for a live dependency on the sync path, and no
problem at all for reading it as a pattern.

[moko-socket-io]: https://github.com/icerockdev/moko-socket-io

[moko-socket-io-sample]: https://github.com/Alex009/moko-socket-io-sample

[KotSock]: https://github.com/whiterabb17/KotSock

[dyte-io/socketio-kotlin]: https://github.com/dyte-io/socketio-kotlin

[kmp-socketio]: https://klibs.io/project/HackWebRTC/kmp-socketio

[KMP Socket.IO deep dive]: https://rahuljindaltech.medium.com/building-cross-platform-libraries-with-kotlin-multiplatform-kmp-kmm-a-deep-dive-into-socket-io-89e58c3b221c

### Protocol versions - checked

Socket.IO major versions are **not** wire compatible, so the server and both clients have to agree.
Checked on 2026-08-05:

| Part                                   | Version                            | Protocol                             |
|----------------------------------------|------------------------------------|--------------------------------------|
| Nightscout `cgm-remote-monitor` 15.0.7 | `socket.io ~4.5.4`                 | **v4**                               |
| AAPS today, Android                    | `io.socket:socket.io-client:2.1.2` | v3 / v4 - correct                    |
| iOS should use                         | `Socket.IO-Client-Swift` **16.x**  | v3 / v4                              |
| moko-socket-io 0.6.0, iOS half         | `Socket.IO-Client-Swift ~> 15.2.0` | **v2 - will not talk to Nightscout** |

`socket.io-client-java` 2.x speaks Socket.IO 3 / 4. On the Swift side that generation only arrives
in **16.0.0** (February 2024, "now supports Socket.IO 3 servers"); 15.x is Socket.IO 2 only.

So **moko-socket-io cannot be used against Nightscout as it is**, and simply bumping its pod to 16.x
is not a fix either - v15 to v16 had breaking API changes (there is an official `15to16` migration
guide), so moko's own wrapper code would not compile against it.

Writing our own wrapper avoids the whole question, because we pick both versions ourselves:
`socket.io-client:2.1.2` unchanged on Android, `Socket.IO-Client-Swift` 16.x on iOS.

Useful escape hatch: the Swift 16.x client can still reach a Socket.IO 2 server by passing
`.version(.two)` to the manager. So 16.x is the right choice even for someone running an old
Nightscout.

### One more thing to check before writing it

**The two clients may not behave the same.** `NSClientV3Service` already carries a comment that
*"java io.client doesn't support multiplexing, create 2 sockets"*, and a leak note about
`Manager.nsps` being a process-static, never-pruned cache. Whether the Swift client shares those
quirks is exactly what an `expect` / `actual` boundary hides until it bites.

---

## 4. Options

### A - Native SwiftUI client, no shared code

A follower reads Nightscout, shows it, and writes treatments back. That is a REST and socket
contract, not AAPS code. LoopFollow and Nightguard already do this. Cheapest, but nothing is
shared and the two apps drift apart forever.

### B - Shared KMP core, SwiftUI on top

Share `:core:data`, `:core:keys`, a Ktor Nightscout client, the Room layer, and the calculations
(IOB / COB, profile, TIR / TDD). UI written twice.

### C - KMP plus Compose Multiplatform  <- recommended

Share logic **and** UI. This is more realistic here than in most projects because the UI is already
Compose and 84% of `:core:ui` has no Android or DI coupling.

The first thought was that this drags the whole repo onto KMP. **That was wrong.** A
`kotlin("multiplatform")` module with `androidTarget()` publishes a normal Android variant, so
`:app`, `:plugins:*` and the pump drivers keep consuming it unchanged. The KMP plugin applies per
module, not per repo.

The decisive argument for C is maintenance, not the initial cost: with two UIs, every future screen
has to be built twice, in two languages, forever.

### Scope note

A client does not need all ~50 modules. Roughly a dozen: `:core:data`, `:core:keys`,
`:core:interfaces` (narrowed), `:core:ui`, `:core:graph`, `:database:*`, the Nightscout sync path
and the overview UI. Pump drivers, automation, SMS, Garmin and wear never need to be KMP.

### One honest warning

Compose Multiplatform on iOS is stable, but it does not feel like a native iOS app. Scrolling,
text fields and accessibility differ from SwiftUI. For an app that people build themselves this is
probably an acceptable trade, but **test it on a real iPhone early**, not after months of work.

---

## 5. Practical setup for iOS

### Hard requirements

1. **A Mac.** Xcode runs only on macOS. Needed for every option, even Compose Multiplatform, because
   the app shell, signing and device runs all go through Xcode.
2. **Apple Developer Program, 99 USD per year.** A free Apple ID works but certificates expire after
   **7 days**, there is no TestFlight, and **push notifications are not available**. Since push is
   the only real answer to the iOS background limits, the paid account is in practice required.

### Where the code should live

```
AndroidAPS repo (this one)
  shared/client-core/         <- the only module with kotlin("multiplatform")
                                 nothing else in the repo changes
        |  CI builds an XCFramework and publishes it (SPM)
        v
AAPSClient-iOS repo (new)
  AAPSClient.xcodeproj
  Sources/                    <- Swift, or a thin shell around Compose Multiplatform
```

Reasons:

- The shared Kotlin **must** stay in this repo. It is taken from the existing code and would rot in
  a separate repo within one release.
- The iOS app should **not** be here. An `iosApp/` folder would force the KMP toolchain on every
  Android contributor and a Mac runner on the main CI.
- AndroidAPS is public, so **GitHub Actions macOS runners are free** for building the framework.

### Tools

| Task                       | Tool                                     |
|----------------------------|------------------------------------------|
| Shared Kotlin              | Android Studio or IntelliJ, as today     |
| Swift and SwiftUI          | Xcode (required)                         |
| Run on device or simulator | Xcode                                    |
| Signing and provisioning   | Xcode                                    |
| Compose Multiplatform UI   | Android Studio, Xcode only for the shell |

### What to learn

- **Swift** is close to Kotlin. Optionals, closures, `struct` / `class`, `protocol` as interface.
  One to two weeks to be productive.
- **SwiftUI** is close to Compose. `VStack` is `Column`, `@State` is
  `remember { mutableStateOf() }`,
  `.padding()` is `Modifier.padding()`.
- **Xcode itself is the painful part**, not Swift. Signing, provisioning profiles and entitlements
  are where the first days go.
- For KMP, use **SKIE** (Touchlab, free). Without it Kotlin `suspend` becomes completion handlers
  and `Flow` does not bridge to Swift at all.

### Distribution

The App Store is not realistic for this kind of app. The working model already exists in this
community: **the Loop approach** - the user forks the repo, puts their own Apple credentials into
GitHub secrets, and GitHub Actions builds and uploads to **their own** TestFlight. Builds last
90 days and renew automatically. No Mac needed by the end user.

This is real work, not an afterthought. Plan for it.

---

## 6. Resources and translations

> **Superseded by wave 14 - compose-resources is not being used.** This section works through what
> adopting it would mean, and the conclusion turned out to be wrong for a reason it never considered:
> the same `values-XX` folder names *parse* under compose-resources but do not *match* a request that
> carries no region, which is what this app sends for 22 of its 25 languages. The current answer is
> simpler than anything below - **Android keeps AAPT and nothing moves**, and only a platform neutral
> name crosses the boundary. The three problems listed further down are still real, and are still the
> reasons compose-resources was rejected. Keep reading for those; ignore the migration plan.

**Translations survive almost unchanged.** Compose Multiplatform Resources uses the same
`strings.xml` format and the same `values-XX` folder names as Android.

```
core/ui/src/commonMain/composeResources/
  values/strings.xml            <- English
  values-cs-rCZ/strings.xml     <- the same files as today
  values-de-rDE/strings.xml
  drawable/  font/  files/
```

**Crowdin only needs a path change** in `crowdin.yml`, from
`src/main/res/values-%android_locale%/strings.xml` to
`src/commonMain/composeResources/values-%android_locale%/strings.xml`. Translation memory and the
existing translations are untouched.

### Three real problems

1. **`getString()` outside Compose is `suspend`.** In a Composable, `stringResource(Res.string.x)`
   is normal. Everywhere else (ViewModels, workers, notifications, Nightscout upload) the function
   is suspending. `ResourceHelper.gs()` is used from about 60 files in `:implementation` and 60 in
   `:plugins:sync`, and those are not Composables.
   Two answers, both worth using:
    - Keep user visible strings **out of shared logic**. Return typed errors or enums and map them
      to
      text in the UI layer. Same idea as the existing rule about keeping `@ColorInt` out of domain
      models.
    - Where a string really must be resolved off the UI thread, `moko-resources` gives synchronous
      access through `StringDesc`.
2. **iOS needs `CFBundleLocalizations` in `Info.plist`.** Without it iOS reports the wrong preferred
   language and resource lookup quietly falls back to English.
3. **`Res` is generated per module**, so `:core:ui` and `:ui` each have their own. Same situation as
   today with `app.aaps.core.ui.R` and `app.aaps.ui.R`. It also needs `publicResClass = true`, or
   the
   generated `Res` is internal and another module cannot see it.
4. **No public locale override.** `ResourceEnvironment` has an `internal` constructor and
   `getSystemResourceEnvironment()` takes no parameters, so there is no supported way to ask for "
   the
   English text" while the UI is in another language. `SearchIndexBuilder` does exactly that,
   through
   `rh.gsNotLocalised(id)`, so for that one caller compose-resources is a feature loss rather than a
   port. This is the problem that decided wave 10.

### What this means for `:core:keys`

The 368 strings themselves move without trouble. The real work is that the key classes carry
`@StringRes Int`, and a Compose Multiplatform resource is a `StringResource`, not an `Int`. That
type change is the job, not the translations.

**Done, in wave 10 - but not with `StringResource`.** The key classes now carry `TextRef`, a two
case
sealed interface owned by `:core:keys` with no Android and no compose-resources types in it. Putting
`StringResource` directly on the keys was tried and rejected: problem 1 above is fatal for it, and
there is a fourth problem the list above misses - compose-resources has **no public locale override
**,
so the always-English search index could not be built at all. `TextRef` moves the type change to the
call sites once, and leaves the choice of resource system to each module, later.

---

## 7. Suggested order of work

**Do not remove all blockers first and start KMP afterwards.** That is how large migrations die:
a year of work against a static list, fixing things that turn out not to matter, with no feedback.

| Step  | Work                                                                                                                                                                                                             |
|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **0** | **Thin slice on a real iPhone first.** `:core:data` to KMP, a small Ktor call to Nightscout, one Compose Multiplatform screen showing a glucose value. Weeks, not months, because `:core:data` is already clean. |
| 1     | `DecimalFormat` and `TimeUnit` cleanup (see section 8)                                                                                                                                                           |
| 2     | `:core:keys` off resource ids - this gates everything in `:core:ui`                                                                                                                                              |
| 3     | Narrow common interfaces out of `:core:interfaces`                                                                                                                                                               |
| 4     | `:core:ui` to KMP, resources to compose-resources                                                                                                                                                                |
| 5     | Ktor Nightscout client, Room KMP                                                                                                                                                                                 |
| 6     | Xcode shell, Koin or kotlin-inject, SKIE, first real screen                                                                                                                                                      |

Step 0 proves the toolchain works, gives an honest answer about how Compose Multiplatform feels on
iOS, and makes every later step demand driven: a blocker is removed because it stands between you
and the next screen, not because it is on a list.

**Where this stands.** Steps 1, 2 and 5 are **done**, and step 0 is half done - out of order, because
`:core:nssdk` turned out to be sliceable after all. Three modules now build for Kotlin/Native:

| Module        | State                                                                                  |
|---------------|----------------------------------------------------------------------------------------|
| `:core:data`  | multiplatform, 2 seams, **real iOS targets**, 15 tests run on Native (waves 5, 15)      |
| `:core:nssdk` | multiplatform, 72 files in `commonMain`, **real iOS targets** (waves 6-9, 15)           |
| `:core:keys`  | multiplatform, 47 files in `commonMain`, **real iOS targets** (wave 14)                 |

All three build for `iosArm64` and `iosSimulatorArm64` on Windows. Together they are the data models,
the complete Nightscout read/write client and the preference keys - the whole spine a follower needs
below the UI - and the entire platform-specific surface under them is **four** `actual`s.

Nothing is left of the original blocker list inside `:core:nssdk` - `org.json`, Gson, joda,
Retrofit,
OkHttp, `android.*` and `java.io` are all gone from it.

**Step 2 is done (wave 14).** `:core:keys` hands out `TextRef` rather than bare resource ids
(wave 10), owns only strings it actually uses (wave 11), nothing outside it reads its `R` class
(wave 12), and it is now a `com.android.kotlin.multiplatform.library` targeting android + jvm +
iosArm64 + iosSimulatorArm64. Its strings never moved: they are still AAPT resources in
`src/androidMain/res`, and a generated name/id pair is what crosses the platform boundary.

That leaves steps 3, 4 and 6. **Step 0's remaining half is smaller than this note assumed**: Apple
klibs cross compile on Windows, so "does it compile for iOS" is answerable locally and already
answered for three modules. What still needs a Mac is running iOS tests, linking a framework, and the
only question that really matters - how Compose Multiplatform *feels* on a real iPhone.

---

## 8. Work done so far

Committed on `dev`:

| Commit       | What                                                  |
|--------------|-------------------------------------------------------|
| `e5f4e27626` | Migrate DecimalFormat                                 |
| `a42d823c93` | Eliminate TimeUnit                                    |
| `e1068e77db` | `:core:keys` remove JVM dependency                    |
| `35b5399798` | Extract dependencies                                  |
| `1aed547f7a` | cleanup (`TB.isInProgress` moved out of `:core:data`) |

Committed on `kmp/core-data-experiment`, a throwaway branch kept because the work turned out to be
worth keeping (see waves 5 and 6):

| Commit       | What                                                                               |
|--------------|------------------------------------------------------------------------------------|
| `67ecb1e696` | Going KMP - `:core:data` is a real multiplatform module                            |
| `e24476237b` | Prepare `:core:nssdk` - dead code out, defaults in, characterization tests         |
| `f6a4e85e8a` | `:core:nssdk` `org.json` -> kotlinx                                                |
| `350f486be4` | `:core:nssdk` Gson -> kotlinx.serialization                                        |
| `cb7b8fa924` | `:core:nssdk` date parsing, eliminate joda                                         |
| `e92ec082d3` | `:core:nssdk` tests - the contract suite, written against Retrofit before the port |
| `ed9c87599f` | `:core:nssdk` Ktor migration                                                       |
| `37e146861f` | version `4.0.0-dev-b-kmp`                                                          |
| `307b1ed615` | `TextRef` (wave 10)                                                               |
| `defd131dec` | `:core:keys` eliminate dependencies (wave 11)                                     |
| `95da0fa7ca` | more `TextRef` migration (wave 11)                                                |
| `6fdb924e6b` | `:core:keys` String migration (wave 12)                                           |
| `c8c1069817` | Fix Danish language selection - `dk` matched no folder, see section 9a            |
| `29dd6ff33e` | `:core:keys` `TextRef.Named` - generated names + id map, 342 call sites (wave 14) |
| `eeddd8182c` | `:core:keys` kmp - multiplatform module, real iOS targets (wave 14)               |

### Wave 1 - `DecimalFormat` removed

Survey found **177 uses in 74 files**, 19 different patterns, and **29 places where
`java.text.DecimalFormat` appeared in an API signature** - including Compose parameters such as
`valueFormat: DecimalFormat = DecimalFormat("0.0")` in `SliderWithButtons`, `PlusMinusEdit`,
`ValueInputDialog`, `NumberInputRow` and five more in `:ui`. Those signatures were blocking
`:core:ui` from `commonMain`, so this step is on the critical path for Compose Multiplatform.

**What was added** in `:core:data`:

- `app.aaps.core.data.format.NumberFormat` - pure Kotlin. Holds `minIntegerDigits`,
  `minFractionDigits`, `maxFractionDigits` plus named constants for all 17 patterns found
  (`INTEGER`, `DECIMAL_1` to `DECIMAL_3`, `DECIMAL_6`, `UP_TO_2_DECIMALS`,
  `DECIMAL_1_UP_TO_2`, ...).
- `NumberFormatPlatform` - the only remaining user of `java.text`. Becomes `expect` / `actual` when
  `:core:data` goes KMP.

**The formatting is not reimplemented.** Matching `DecimalFormat` half-even rounding on doubles bit
for bit in pure Kotlin is hard, and the test suite cannot detect a mistake (see the locale note
below). Delegating gives the KMP win with no change in behaviour.

Two details that would have caused silent bugs:

- `isGroupingUsed = false`. `DecimalFormat()` groups by default, so `1234.5` would have become
  `1,234.5`.
- A `ThreadLocal` cache. `DecimalFormat` is not thread safe, and `StringUtil` currently shares one
  mutable array between threads.

**The cache key must hold the locale.** A first version keyed it on (format, separator) only. A
cached `DecimalFormat` carries the WHOLE symbol set, not only the separator, and the app can change
language while it runs (`ComposeMainActivity` calls `recreate()`, the process stays alive). Swedish,
Norwegian and Lithuanian use a comma AND a real MINUS SIGN (U+2212); German and Czech use a comma
and a plain hyphen. Same key, different symbols, so after a language switch negative numbers kept
the old sign until the app was killed. Fixed by putting `Locale` in the key. The test
`symbols follow the locale after it changes` switches de -> sv -> de and en -> ne-NP and checks both
the sign and the digit shapes.

**Locale behaviour is unchanged on purpose.** The separator comes from the platform, so a Czech or
German device still shows a comma. `format(value, SEPARATOR_DOT)` is the explicit way to ask for a
dot, for server data and files.

**Result:** 57 files across 11 modules migrated, all 29 API leaks closed, `:app` and `:wear`
compile,
**2319 unit tests pass with 0 failures**.

**A latent bug was found and neutralised.** 13 automation triggers used `DecimalFormat("1")` and
`DecimalFormat("0.1")`. Those are not digit placeholders - the digits are literals:

```
"1"    format(100.0) -> "1100"    format(-5.5) -> "-16"
"0.1"  format(720.0) -> "720.1"   format(0.5)  -> "0.1"
```

It never reached users, because `decimalFormat` in `InputDouble`, `InputDelta` and `InputBg` is a
private field that is only written, never read. This was checked properly afterwards, including the
JSON that triggers write to preferences and sync - see "Was behaviour preserved?" below. The dead
parameter is worth deleting separately.

**Two files were left alone on purpose:**

| File                                      | Reason                                                           |
|-------------------------------------------|------------------------------------------------------------------|
| `wear/SmallestDoubleString.kt`            | Builds patterns from a runtime string, needs real logic          |
| `shared/impl/src/test/DateUtilOldImpl.kt` | A frozen copy of the old implementation kept as a test reference |

### Two locale bugs fixed on the way

**`StringUtil.getFormattedValueUS` is now the extension `Number.formatUS(decimals)`.** It builds the
`DecimalFormat` with `DecimalFormatSymbols(Locale.US)` instead of formatting with the device locale
and then replacing a comma with a dot. That replace did nothing on locales that use another
separator, for example Arabic. A new test checks the output in `en`, `de-DE`, `cs-CZ`, `fr-FR` and
`ar-SA`.

It stays on `java.text` and **adds no module dependency**. `:core:utils` carries Firebase,
WorkManager, spongycastle and tink, so it is not a KMP candidate anyway, and a
`:core:utils -> :core:data` edge would only slow the build down.

The old shared `DecimalFormatters` array is gone too. `DecimalFormat` is not thread safe, so two
threads formatting at the same time could produce wrong text.

**`DateUtilImpl.qs()` no longer groups.** The old code used `DecimalFormat()` with no pattern, which
groups by default, and only the decimal separator was overridden. The grouping separator stayed
locale dependent, so for values over 1000:

| locale | `qs(1234.5, 1)` before | after    |
|--------|------------------------|----------|
| en     | `1,234.5`              | `1234.5` |
| de     | `1.234.5` (two dots)   | `1234.5` |
| cs     | `1<nbsp>234.5`         | `1234.5` |
| ar     | Arabic-Indic digits    | `1234.5` |

Grouping was never intended - the method forces a dot separator, so it clearly wants neutral output.
Its only caller is `niceTimeScalar` ("3 days", "45 minutes"). No grouping support had to be added to
`NumberFormat`.

### Wave 2 - duration types (done)

**`java.util.concurrent.TimeUnit` really is a blocker** - it does not exist on Kotlin/Native. Of 226
uses in 108 files, **94 were constant conversions and are now `kotlin.time`**:

```
TimeUnit.MINUTES.toMillis(x)       ->  x.minutes.inWholeMilliseconds
TimeUnit.MILLISECONDS.toMinutes(x) ->  x.milliseconds.inWholeMinutes
```

51 files changed. The other 111 uses stay, because the API they are passed to demands a `TimeUnit`:
`Single.timeout` (36), `WorkRequest.setInitialDelay` (8), `Semaphore.tryAcquire` (7), RxJava
schedulers, OkHttp timeouts. Those files keep the import.

Two traps that a plain search and replace would have fallen into:

- `plugins/automation/elements/InputDuration.kt` declares its **own** `enum class TimeUnit`, and
  `ActionStartTempTarget.kt` imports both it and `java.util.concurrent.TimeUnit`. The local one is
  always written as `InputDuration.TimeUnit.MINUTES`, so matching on `TimeUnit.<UNIT>.to<Unit>(`
  cannot hit it - but a rename over the bare word would have broken it.
- Three calls in `:pump:eopatch` nest one `TimeUnit` call inside another, for example
  `TimeUnit.MILLISECONDS.toMinutes(millis - TimeUnit.HOURS.toMillis(hours))`. Those were done by
  hand, since the argument needs balanced bracket matching, not a regex.

**`T` (`core/data/time/T.kt`) was left as it is.** It is imported by 221 files, and it is **not** a
KMP blocker: it is pure Kotlin in a pure Kotlin module. `T.now()` was the only line using a JVM API
(`System.currentTimeMillis()`), and it turned out to have **no callers at all** apart from its own
test, so it was deleted. `T` is now fully portable as written, and nothing forces a migration.

Replacing `T` with `kotlin.time.Duration` everywhere was tried and then reverted. It gives no
portability gain, and deprecating a class used by 221 files costs about that many build warnings for
everyone until the files move over. If it is ever done, keep the quirk that `T.months(n)` counts a
month as 31 days.

### Wave 3 - `:core:keys` off the JVM (done)

`ComposedKey.composeKey` used `String.format(Locale.ENGLISH, key + format, *arguments)`. It is now
plain Kotlin that walks the template itself.

Two reasons, and the first matters more than the portability one:

1. The result becomes a **SharedPreferences key name**. `String.format` follows a locale, and a
   locale with its own digits (Nepali, Bengali, Burmese, ...) would write `%d` in that script. The
   key would differ and the stored setting would be lost. `Locale.ENGLISH` was passed to avoid that;
   building the text by hand removes the risk instead of working around it.
2. `String.format` is JVM only.

Every format in the app is `%d` or `%s` (19 uses, nothing else), so no library and no `expect` /
`actual` is needed. Anything else now **throws** `IllegalArgumentException`: an unknown specifier, a
lonely `%`, a wrong argument count, or a `%d` given something that is not a whole number. That last
guard matters - `String.format("%d", 1.5)` used to throw, while plain substitution would have
quietly produced a different key and lost the setting.

`ComposedKeyTest` compares the new output against the real `String.format(Locale.ENGLISH, ...)` for
**every composed key enum in the app**, iterating `.entries` so a new key is covered automatically,
and repeats it in `ar-SA`, `ne-NP`, `bn-IN`, `my-MM`, `fa-IR` and `th-TH-u-nu-thai`.

`:core:keys` had no test source set, so `id("test-module-dependencies")` was added. Test only, no
effect on the main compile.

**What is left in `:core:keys`: nothing but resources.** Zero `android.*` imports, zero `java.*`
imports. The only blocker is **381 `R.string` references in 6 files** - the keys carry preference
titles and summaries as raw `Int` resource ids. That is the resources job in section 6, not a
separate problem.

> **That paragraph was wrong**, and it was wrong in a way worth remembering. It came from grepping
> `^import android` / `^import java`, which finds nothing when the type is written **fully qualified
> inline** or is **auto-imported**. Wave 11 found two real ones:
> `android.content.Context` in `StringPreferenceKey.kt` (written fully qualified, so no import line)
> and `java.lang.Class` in five places (`java.lang` needs no import). The same mistake shape recurred
> twice more later - see the note at the end of wave 12. Grep for *usage*, not for imports.

### Wave 4 - `:core:data` ambient state (partly done)

Two reads of ambient system state were removed from the models:

- **`TB.isInProgress`** was a property computing `System.currentTimeMillis() in timestamp..end`. It
  is now `fun TB.isInProgress(dateUtil: DateUtil)` in `:core:objects`, matching the `EB` sibling two
  lines away in the same file, which already worked that way. One call site. Besides portability it
  removes a hidden non-determinism: the same object could compare unequal to itself across a tick,
  and it could not be tested without waiting for real time.
- **`DoseStepSize.description`** used `String.format(Locale.ENGLISH, "%.3f", ...)`; it now uses
  `NumberFormat.DECIMAL_3` with `SEPARATOR_DOT`. The Wave 1 work had already built the replacement.

**`java.util.TimeZone` in 17 model files stays, and should be solved with `expect` / `actual`.**

Every one of them is the same line:

```kotlin
var utcOffset: Long = TimeZone.getDefault().getOffset(timestamp).toLong(),
```

Removing the default and passing `utcOffset` in from the callers was tried and **reverted**. Why it
is the wrong shape here:

- `:core:data` cannot use `DateUtil`. `DateUtil` lives in `:core:interfaces`, which already depends
  on `:core:data`, so the reverse edge is a cycle. And a data class default parameter has nowhere to
  receive an injected dependency anyway.
- The ripple is large. The compiler first reported 2 errors; the real count was 38 and still growing
  when the attempt was stopped, with test sources not yet compiled. Gradle stops at the first
  failing
  module and Kotlin caps reported errors per file - in `PersistenceLayerImpl` it named 10 of 31.
- **`utcOffset` is not a throwaway field.** It is stored in every table, it is part of
  `contentEqualsTo` (so a changed value makes a record compare unequal and re-sync), it is uploaded
  to Nightscout - where the server validates it and `NSAndroidClientImpl` has to catch
  `"Bad or missing utcOffset field"` 400s and retry with 0 - it goes to Open Humans, and it is shown
  in the user entry history. Recomputing it by hand at 50+ sites risks silently changing stored
  values; `expect` / `actual` keeps the computation identical at every site and changes no call
  site.

So when `:core:data` becomes multiplatform:

```kotlin
// commonMain
expect fun systemUtcOffsetAt(timestamp: Long): Long   // the models keep their default
// jvmMain   -> TimeZone.getDefault().getOffset(timestamp)
// iosMain   -> NSTimeZone
```

The same reasoning does **not** apply to `isInProgress`. That is a value computed on demand at
arbitrary times, so passing `DateUtil` is right there. A constructor default evaluated once at
creation is the weaker case, and the 154 call sites that care already pass `utcOffset` explicitly.

**State of `:core:data` now:**

| Was                             | Now                                                |
|---------------------------------|----------------------------------------------------|
| `System.currentTimeMillis()`    | gone (`T.now()` deleted, `isInProgress` moved out) |
| `java.util.Locale`              | gone (`DoseStepSize`)                              |
| `java.util.concurrent.TimeUnit` | gone (Wave 2)                                      |
| `java.util.TimeZone`, 17 files  | `expect` / `actual`, no call site changes          |
| `NumberFormatPlatform`          | `expect` / `actual`, by design                     |

Two `expect` / `actual` declarations away from compiling as `commonMain`.

### Wave 5 - `:core:data` really is multiplatform (done)

`:core:data` now uses `kotlin("multiplatform")` with a `jvm()` target and `mingwX64()` as a stand-in
for iOS (real iOS targets need macOS and Xcode, so Windows cannot build them - `mingwX64` proves the
code compiles for Kotlin/Native, which is the part that was in doubt).

**Zero consumer changes.** All 13 modules that depend on `:core:data` build unmodified: an Android
consumer still sees a normal library, because a multiplatform module publishes a normal variant.
That was the single most valuable thing to learn, and the reason it was worth doing on a branch.

Two `expect` / `actual` seams, both deliberate:

| Seam                           | Why                                                                      |
|--------------------------------|--------------------------------------------------------------------------|
| `NumberFormatPlatform`         | number formatting is genuinely platform work                             |
| `systemUtcOffsetAt(timestamp)` | replaces `TimeZone.getDefault()` in 17 model files, no call site changed |

The `mingwX64` target needed one opt-in, `kotlin.experimental.ExperimentalNativeApi`, because
`ICfg.iobCalcForTreatment` uses `assert()`.

Verified on WSA: all four flavours install, run, and sync against a live Nightscout.

### Wave 6 - `:core:nssdk` off `org.json` (done)

**Worth recording, because the first assessment was wrong.** The module was written off as something
that could not be salami-sliced: the Gson annotations, the joda date parsing, the `IOException`
hierarchy and the converter all form one wire-format contract, so the argument went that it had to
move in a single step or not at all. That is true *of the converter*, but the converter is not the
only seam. The carrier type is a separate one, and it came out first, alone, with the converter
untouched. The lesson generalises: "these things are coupled" is a claim about one axis, and it is
worth checking whether some other axis cuts cleanly before accepting a big-bang migration.

`org.json` is a JVM and Android API. It was in the **public API** of `NSAndroidClient` (10 methods)
and `RunningConfiguration` (2), carrying profiles and settings - the two document kinds AAPS does
not
model, because it does not own their shape. It is now gone from the module.

Why it could be done alone: both directions already round-tripped through text, so `org.json` was
only ever a carrier.

```kotlin
JSONObject(json.asJsonObject.toString())                      // read  - Gson tree -> text -> org.json
api.createSetting(JsonParser.parseString(doc.toString()))     // write - org.json -> text -> Gson tree
```

Swapping the carrier for kotlinx `JsonObject` left the write line **character for character
identical** and changed one parse call on the read side. Gson, Retrofit and the 210
`@SerializedName`
annotations were not touched.

**The trap, and why tests came first.** The two libraries disagree about a missing key, and nothing
about the difference shows up at compile time:

| accessor        | `org.json`, missing key | `org.json`, explicit `null`     | kotlinx, missing key |
|-----------------|-------------------------|---------------------------------|----------------------|
| `optString`     | `""`                    | `"null"` - the four letter text | `null`               |
| `optJSONObject` | `null`                  | `null`                          | `null`               |
| `optLong(k, 0)` | `0`                     | `0`                             | `null`               |
| `optBoolean`    | `false`                 | `false`                         | `null`               |

A straight translation would silently flip every downstream `isEmpty()` and `?:`. So the swap was
done through `OrgJsonCompat`, kotlinx accessors that reproduce `org.json` exactly, golden-mastered
against the real thing over 21 inputs x 5 accessors. The type change is then equivalent by
construction rather than by inspection.

The one genuine divergence: reading an **object** through `optString` differs in key order, because
`org.json` iterates a hash map and its order is unspecified. No call site does it - all six keys
read
through `optString` hold strings - so it is documented and skipped rather than pinned.

**What stays on `org.json`, on purpose.** `JsonBridge` marks the two boundaries:

- **socket.io** hands every payload over as `org.json.JSONObject`. That is the library's API, so the
  conversion happens as the event arrives and everything downstream is kotlinx.
- **The profile subsystem** - `ProfileStore`, `PureProfile`, `DataSyncSelector.PairProfileStore` -
  is
  `org.json` throughout `:core:interfaces`. Converting it is its own project. Profiles are converted
  where they cross into the client instead.

Both live in `:plugins:sync`, which is Android only by nature (WorkManager, a socket.io `Service`),
so the `org.json` quirks stay out of the shared modules.

**Dead code removed on the way:**

- `RemoteProfileStore` - an abandoned attempt to model the profile store as a typed class, with a
  commented-out `Store` / `SimpleProfile` / `ProfileEntry` model still in the file. Zero consumers.
  This was the "nested JSON I could not get working with kotlinx" that came up in discussion - the
  archaeology was still in the file.
- `NSAndroidRxClient` - dead, and `kotlinx.coroutines.rx3` went with it.
- Both `?: return@Listener` guards in `NSClientV3Service.onDataDelete`. `optString` never returns
  null, so neither could ever fire; Kotlin allowed them only because `org.json` is Java and the type
  is the platform type `String!`.

**Immutability was the only real code change.** kotlinx `JsonObject` cannot be mutated, so six
in-place `put` calls became rebuilds. Five were mechanical; the sixth was not -
`RunningConfigurationPublisher` mutated a *nested* object to mask `NsClientAllowClientControl`
inside
`syncedPrefs`, and that is now an explicit rebuild preserving key order and the masking semantics.

The wire format's mixed typing was preserved deliberately: `isFakingTempsByExtendedBoluses` is a
real
JSON boolean while every `syncedPrefs` value is a string, exactly as `org.json` wrote them.

**Left unfixed, on purpose.** `optStringCompat` faithfully reproduces the `"null"` quirk, so a
server
sending `{"message":null}` still writes the word "null" into the user visible NSClient log. Changing
behaviour during a type migration is how regressions get smuggled in; that is a separate change.

**What is left in `:core:nssdk`:**

| Dependency            | Files | Slice                                                  |
|-----------------------|-------|--------------------------------------------------------|
| Gson                  | 17    | the converter switch                                   |
| joda-time             | 1     | with the converter - `RemoteTreatment` lenient dates   |
| Retrofit              | 4     | Ktor                                                   |
| OkHttp                | 3     | Ktor                                                   |
| `java.io.IOException` | 1     | with Ktor - the exception hierarchy                    |
| `android.*`           | 2     | with Ktor - mainly `Context` for the OkHttp disk cache |

Verified on WSA with a full sync. That specifically exercises the riskiest part: settings and
profile
reads both go through the changed Gson type adapter, and kotlinx `JsonObject` also implements
`Map<String, JsonElement>`, so Gson picking its built-in map adapter instead of the registered one
was a real possibility that would have failed quietly rather than thrown.

### Wave 7 - `:core:nssdk` off Gson (done)

190 `@SerializedName` -> `@SerialName`, `@Serializable` on 19 classes, and the Retrofit converter
swapped for `converter-kotlinx-serialization`. That artifact is **official and ships with Retrofit
3.0.0**, same group and version as the Gson converter it replaces, so it needed no third party
dependency - and it goes away with Retrofit itself when the client moves to Ktor.

Two things got smaller rather than bigger:

- The Gson `JsonDeserializer` that built `JsonObject` is **gone**. kotlinx reads `JsonObject`
  natively, so the adapter, the `GsonBuilder` and the `Gson` instance all went with it.
- `DeviceStatusMapper` used to rebuild all four schema-less subtrees (`pump.extended`,
  `openaps.suggested` / `enacted` / `iob`) by printing each to text and parsing it back, because one
  side was a Gson tree and the other a kotlinx tree. Both sides are kotlinx now, so those seven
  conversions became a straight assignment.

#### The configuration is the whole decision

`NsSdkJson.kt` holds one `Json` instance shared by Retrofit and the string mappers. Every flag is
there to match Gson, not because it is a good default in the abstract:

| Flag                    | Why                                                                              |
|-------------------------|----------------------------------------------------------------------------------|
| `ignoreUnknownKeys`     | documents carry fields this version has never seen                               |
| `explicitNulls = false` | Gson omits nulls; NS rejects some explicit nulls                                 |
| `isLenient`             | one measured case: a bare **number** arriving in a `String` field (`created_at`) |
| `encodeDefaults`        | **backward compatibility** - see below                                           |
| `coerceInputValues`     | **forward compatibility** - see below                                            |

Two of those five were added only because a test failed, and both are the difference between working
and quietly breaking somebody else's device:

- **`encodeDefaults`** - kotlinx's default is the **opposite** of Gson's. Gson writes every non-null
  field; kotlinx omits any field equal to its default. Without this, `isReadOnly = false`,
  `duration = 0`, `utcOffset = 0` and every `LastModified.Collections` counter simply stop being
  written. Absent is not the same as false to a reader that has not been updated.
- **`coerceInputValues`** - an **unknown enum value** throws in kotlinx but was mapped to `null` by
  Gson. `eventType` is an enum. A *newer* AAPS adding one event type would otherwise make every
  older
  client throw on that record, inside a socket.io listener with no try/catch.

Only one case needed `isLenient`. Strict kotlinx already accepts a quoted number in a `Long` /
`Double` / `Int` field and `"true"` in a `Boolean` field, which was a pleasant surprise - the gap
between the two libraries is much narrower than it looks.

#### The trap that nearly shipped

**A non-null field with no default is optional under Gson and mandatory under kotlinx.** Gson builds
objects with `Unsafe.allocateInstance`, never calls the constructor, and leaves such a field as
`null` / `0` / `0.0`. kotlinx treats the identical declaration as required and throws.

Neither `coerceInputValues` nor `explicitNulls` rescues it - **both only apply to properties that
already have a default.** And a page is decoded in one pass, so one bad document loses the whole
page, not one record.

`RemoteFood` had four such fields. Nightscout's `food` collection also stores **quickpick**
documents written by the NS food editor, which have no `portion` and no `carbs` - `FoodMapper`
already has an `else -> return null` branch for exactly those, but the parse now died before the
mapper ran. Measured: a list of `[good food, quickpick]` returned **zero** items. `LoadFoodsWorker`
then fails, and it sits mid-chain, so it also cancels the profile, settings and devicestatus workers
every fifth loop.

`RemoteEntry.type` was the same shape on the glucose feed, where `LoadBgWorker` only advances its
cursor after a successful decode - so one typeless record would have stopped all glucose and
re-requested the same page forever.

Fixed by giving those fields the values Gson used to leave behind (`""`, `0`, `0.0`), which restores
the old tolerant behaviour exactly. `encodeDefaults = true` means the upload format does not change.

**This was found by an adversarial audit, not by the tests.** The characterization tests were
written specifically to catch behaviour changes and they missed it, because there was **no food
decode test at all** - `FoodExtensionKtTest` is object-to-object and `LoadFoodsWorkerTest` mocks
`getFoods()`. A test suite only pins the paths somebody thought to write a test for.

#### Verified on a device, both versions at once

A Pixel emulator ran a **new master against a pre-KMP client** on a live Nightscout instance. Every
record type that can be created by hand was created and followed end to end:

| Record                                      | Old client result                                   |
|---------------------------------------------|-----------------------------------------------------|
| Carbs                                       | `◄ INSERT`, visible in Treatments history           |
| Bolus (with nested `icfg`)                  | `◄ INSERT Bolus`, visible in Treatments history     |
| Profile switch                              | `◄ INSERT ProfileSwitch` + `EffectiveProfileSwitch` |
| Temporary target (new **and** PATCH update) | `◄ INSERT TemporaryTarget`                          |
| Therapy event                               | `◄ INSERT TherapyEvent`                             |
| Glucose, devicestatus, settings             | received and applied                                |

The actual bytes are the best evidence. This is what the new master uploaded for a 20 g carb entry:

```json
{
  "date": 1786012366166,
  "utcOffset": 0,
  "app": "AAPS",
  "isValid": true,
  "isReadOnly": false,
  "eventType": "Meal Bolus",
  "carbs": 20.0,
  "notes": ""
}
```

`utcOffset:0`, `isValid:true` and `isReadOnly:false` are all present - that is `encodeDefaults`
working. Without it those three vanish. Nulls are omitted, as Gson did.

The strongest single result is the **signed client-control round trip**: the old client sent an
HMAC-signed envelope, the new master verified it and acked, and the old client verified the ack
back.
A signature only verifies if the serialized bytes match, so that is close to proof rather than
inference.

Not covered on the device: temp basal and extended bolus (need real pump or loop activity), and the
**food upload path does not exist** - `QueueCounter` has no food counter, AAPS syncs food
download-only. The food fix rests on unit tests.

### Wave 8 - `:core:nssdk` off Retrofit and OkHttp (done)

The HTTP client moved to **Ktor on the OkHttp engine**, so the app reuses the OkHttp it already
ships
rather than carrying two HTTP stacks. On iOS the engine becomes Darwin and nothing else changes,
which is the whole reason for the move.

#### The map came first, and it was worth it

Before any code changed, five parallel readers mapped what the Retrofit stack actually guaranteed,
and each finding was then attacked by an agent trying to refute it. That produced a written contract
and **30 silent-failure risks**. Three were serious enough to have shipped:

- **The 304 signal only existed because of the OkHttp disk cache.** `response.raw().networkResponse
  ?.code` can only be 304 when the cache revalidates a GET and merges the result into a 200. Ktor
  has
  no equivalent, so `code` would have become a constant 200 - and `LoadBgWorker`'s
  `response.code != 304 && processSgvs(...)` would have become an unconditional process. The paging
  loop never exits, `storeGlucoseValuesToDb()` is never reached, and **BG never lands in the
  database** while the worker looks busy.
- **Static query pairs are hidden inside the `@GET` strings**, not in `@Query` parameters -
  `v3/profile?sort$desc=date&limit=1`. Rebuild the URL from the method parameters and they vanish.
  Losing `limit=1` there makes `LoadProfileStoreWorker` take `profiles[profiles.size - 1]` from an
  unsorted list, silently applying the **wrong profile**: different basal, ISF and IC.
- **The `utcOffset` auto-retry reads the raw 400 body text.** Ktor has no separate `errorBody()`,
  and
  a body read lazily or only on success loses that string. The record is then dropped permanently
  while the sync cursor advances.

#### Tests before code, against the old stack

49 characterization tests were written **while Retrofit was still in place**, using **MockWebServer
**

- a real HTTP server on localhost - rather than Ktor's `MockEngine`. That choice is the point:
  `MockEngine` only exists after the swap, so tests written with it could never have proved anything
  about the behaviour before it. The same files ran unchanged against both stacks.

They pin: every endpoint URL as a literal string; the read/write status asymmetry and its **request
counts**; the `utcOffset` fallback verified by inspecting both request bodies; the auth headers,
refresh and clock-skew paths; ETag parsing; and the `{"result": ...}` envelope, which is applied
inconsistently per endpoint.

Two divergences were caught this way rather than in the field:

|                           |                                                                                       |
|---------------------------|---------------------------------------------------------------------------------------|
| `$` in query keys         | survived on the first try - Ktor's `encodedParameters` leaves it literal              |
| `/` inside a path segment | Ktor does **not** encode it, Retrofit does (`a%2Fb`). Fixed with `encodeSlash = true` |

#### Decisions worth recording

- **`expectSuccess = false`.** Ktor's default throws on any non-2xx, which would make the whole 4xx
  ladder dead code - and because `ClientRequestException` is not in the retry exclusion list, every
  such call would also be retried four times before surfacing.
- **No `HttpRequestRetry`.** It would multiply with the existing `retry()`, and the `utcOffset`
  fallback re-enters a public method, so one reading could produce over ten POSTs in a single sync.
- **Auth is hand written, not Ktor's `bearer` provider.** The provider differs in four ways, two of
  which lose data quietly: it omits the header when it has no token (Nightscout then answers **200
  with the anonymous role**, and uploads stop with nothing logged), and it refreshes on 401 only,
  while Nightscout also answers 403. It also never sees the response body, so the clock-skew error
  could not exist.
- **The 304 brake was replaced, not reproduced.** The workers now stop when the cursor cannot
  advance
  (`lastServerModified <= lastLoaded`), which is what the 304 always meant. Only the modified-since
  path can stall that way; the first load pages by date and carries no server timestamp.
- **`close()` was added** to `NSAndroidClient` and is called from `restartOnChange`. The Retrofit
  client leaked quietly on every URL or token change; a Ktor engine holds real connections.

Also fixed on the way, deliberately and separately: `getVersion` / `getStatus` threw
`retrofit2.HttpException` and `NullPointerException`, neither a `NightscoutException`; they now
throw
`UnsuccessfulNightscoutException`. And a 10 s connect timeout is now explicit - OkHttp applied one
by
default and Ktor does not, so a dead host would otherwise hang for the full 60 s socket timeout.

**Left broken on purpose:** `updateFood` and `deleteFood` declare an `{identifier}` the path does
not
contain, so Retrofit refused to build them and **no request was ever sent** - food edits have never
synced, because the endpoint is broken on the Nightscout side. A hand-written Ktor URL would have
turned "never sends" into `PATCH /api/v3/food` and `DELETE /api/v3/food` **with no identifier**, a
request against the whole collection on a live server. They reproduce the local failure instead,
verified by a test asserting zero requests.

#### Verified on a device

A new Ktor master against a **pre-KMP client** on a live Nightscout: writes accepted (201), reads
across status / lastModified / settings / devicestatus / treatments, socket.io push received, and
the
old Gson/Retrofit client stored what Ktor wrote. The auth flow ran for real - a 401 triggered
`GET /api/v2/authorization/request/<token>` and a 200 - which exercised the hand-written interceptor
and the leading-slash refresh URL together.

Turning the WebSocket **off** was needed to see any of the REST paging at all: with push enabled the
load workers barely run. With it off, `◄ RCV TR END` on the modified-since path confirmed the new
cursor brake terminates instead of spinning.

Not observed on the wire: the `$` operators, because `date$gt` / `created_at$gt` / `sort$desc` only
appear on **first-load** paths and every collection was already synced. They are covered by two test
files asserting the same literal URLs against Retrofit and against Ktor, so the bytes are identical
and Nightscout cannot tell them apart.

### Wave 9 - `:core:nssdk` is multiplatform (done)

Same shape as `:core:data`: `jvm()` plus `mingwX64()` as the Kotlin/Native compile proof. **72 files
in `commonMain`**, and all four consumer modules build unchanged.

Only four things blocked `commonMain`, and none needed design work:

| Blocker                                      | Fix                                                                                                                                                                   |
|----------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@JvmSynthetic` on an `internal` helper      | dropped - it was hiding an already-internal function from Java                                                                                                        |
| `KClass<out java.lang.Exception>` in `retry` | `KClass<out Throwable>`. Exact-class matching and the `catch (Exception)` are unchanged, so which exceptions are excluded and how many attempts happen stay identical |
| `Dispatchers.IO`, 2 sites                    | `expect val nsIoDispatcher`; the JVM actual is the same `Dispatchers.IO` as before                                                                                    |
| the Ktor engine and its logging interceptor  | `expect fun nsHttpClient(...)` - OkHttp on JVM, CIO on Native                                                                                                         |

#### The crypto did not need solving

`ClientControlCrypto` and `ClientControlPairingCrypto` are `javax.crypto` and `java.security`, and
they looked like the blocker. They are not: **nothing inside the module calls them** - the only
internal reference is a KDoc link - so they simply live in `jvmMain`. No `expect` / `actual`, no
stub, no crypto library, and the Native target compiles without them.

The code stays in `:core:nssdk`, declared as its JVM part. When client-control is wanted on another
platform it is a source-set move **within this module** plus actuals, not a redesign.

Golden vectors were extracted first, in `ClientControlCryptoVectorsTest`: fixed inputs with their
exact outputs, so a second implementation can be checked byte for byte rather than by "it is also
HMAC-SHA256". The algorithms are standards and interoperate by definition; the **packaging** is what
differs and what fails silently:

- **GCM tag placement** - JCE returns `ciphertext ‖ tag` from one `doFinal`; Apple's CryptoKit keeps
  the tag separate. The vectors pin `wrapped.size == plaintext.size + 16`.
- **Hex case** - signatures are compared as text, so lower case is part of the wire format.
- **PIN encoding** - ASCII digits today, which is why any encoding agrees. Worth knowing before
  anyone widens the alphabet.

Those values cannot change without breaking every deployed AAPS, so a failure there is a real
incompatibility, not a stale test.

#### What a desktop JVM target would cost

Nothing, for the crypto. The pattern here is a plain `jvm()` target, not `androidTarget()`, so the
single `jvmMain` already serves Android - and `javax.crypto` is Java SE, so Windows, Linux and macOS
desktop builds use the same code untouched. Only Kotlin/Native targets need a second implementation.

**Web would be different**, and it is a decision to take early rather than late: WebCrypto is
async-only, so `sign()` and `wrap()` would have to become `suspend`, and retrofitting that ripples
through every caller.

#### Honest limits of the proof

`mingwX64` is a **compile proof, not a shipping target**: request logging is not wired up there, and
CIO stands in for what an iOS build would use (Darwin). What it does prove is real - 72 files of
wire
layer, models, mappers and HTTP client with no JVM API anywhere in them.

### Wave 10 - `TextRef`, a seam for strings (phase 1 done)

Every user facing string in AAPS is an Android `R.string` id: a plain `Int` handed out by AAPT at
build time. The preference keys alone carry 394 of them - a title and often a summary on each of
about 300 constants, spread over 40 enums in `:core:keys` and the plugins. An iOS or desktop client
has no AAPT and therefore no such ids, so this is the next real blocker after the network layer.

#### The obvious answer does not fit

The KMP answer is Compose Multiplatform Resources: keep the same `strings.xml` and the same
`values-XX` folders, let the Gradle plugin generate a `Res` object per module, and read strings with
`stringResource(Res.string.x)` in Compose or `getString(Res.string.x)` outside it. Translation stays
in Crowdin exactly as it is now, which was the main worry and turned out to be a non-issue.

Two things stop it from going on the key classes themselves:

- **`getString()` is `suspend`.** In Compose that is fine. Outside Compose it is not: a preference
  key's title is read from view models, notification builders and the search index, and none of them
  are suspend today.
- **There is no public way to ask for a specific locale.** `SearchIndexBuilder` builds a second,
  always-English index so a user with a translated UI can still search using the English term they
  read in the docs. Today that is `rh.gsNotLocalised(id)`. compose-resources has a
  `ResourceEnvironment`, but its constructor is `internal` and `getSystemResourceEnvironment()`
  takes
  no parameters, so the English index cannot be built at all. That is a feature loss, not a port.

moko-resources was evaluated as the alternative and rejected: it solves the suspend problem and has
a
real locale override, but it is a third party plugin, it wants its own `MR` object and its own
`StringDesc`, and adopting it means every module on the KMP path takes a dependency on it before any
of them is actually multiplatform.

#### What was built instead

`TextRef` is a two case sealed interface in `:core:keys`, with no Android types in it:

```kotlin
sealed interface TextRef {
    data class Res(val id: Int, val args: List<Any> = emptyList()) : TextRef
    data class Literal(val text: String) : TextRef
}
```

It is deliberately **not** a resource system. It is a seam. `PreferenceKey.titleResId: Int` became
`PreferenceKey.title: TextRef`, and `summaryResId: Int?` became `summary: TextRef?`, so nothing
outside the resolvers passes a bare resource id around any more. There are exactly two resolvers:

- `app.aaps.core.ui.compose.stringResource(ref)` for Compose,
- `ResourceHelper.gs(ref)` / `gsNotLocalised(ref)` for everything else, both default methods on the
  interface so no implementation had to change.

Today `TextRef.Res.id` is always an ordinary AAPT id and both resolvers just pass it through. When a
module later does move its strings to `commonMain/composeResources`, that module's ids can be
encoded as negative tokens and only these two functions learn about it. The call sites do not change
a second time.

`TextRef.Literal` is the other half, and it is what makes the type worth having rather than a
`typealias`: some titles are not resources at all. It also collapses the "resource id **or** already
resolved string" property pairs that had started to appear.

#### Phase 1, and what it cost

The 40 preference key enums were converted by script, and every enum **constant** is byte for byte
unchanged - they still say `titleResId = R.string.x`. Only the constructor parameter turned private
and two property overrides were added after the constants:

```kotlin
override val title: TextRef = TextRef.Res(titleResId)
override val summary: TextRef? = summaryResId?.let { TextRef.Res(it) }
```

That kept all 272 constants and their 394 resource references untouched. `PreferenceSubScreenDef`
got
the same treatment for the same reason - roughly fifty plugin call sites build it with
`titleResId = R.string.x`.

`:core:interfaces` had to change `implementation(project(":core:keys"))` to `api`, because `TextRef`
now appears in a `ResourceHelper` signature. That is not a new module edge, only a widened one.

The `if (titleResId == 0) return` guards in nine composables went with it: a title is now a non-null
`TextRef`, so "no title" is not representable at the call site.

**One of those guards was not dead, and the first check for that was wrong.** Grepping for
`titleResId = 0` finds nothing, which is what the guards were removed on - but 26 key enums
*defaulted* the parameter to `0`, and one constant took the default:
`RileyLinkStringPreferenceKey.MacAddress`. It is storage, not a preference - the pairing wizard
writes it and it is on no screen - so the composables never saw it, but it is registered, so it did
reach `SearchIndexBuilder` and would have been indexed with an empty title.

Rather than guard it, the key moved to where it belonged: `RileyLinkStringKey`, the
`StringNonPreferenceKey` enum sitting next to it that already held the RileyLink device `Name`. The
preference key string, the default and `exportable` are unchanged, so a paired RileyLink keeps its
address and settings export is unaffected - `isExportableKey()` walks all registered enums, and both
were already registered together. `getAllPreferenceKeys()` filters to `PreferenceKey`, so the key
simply stops reaching the index. No guard, no magic number.

With the last user of the default gone, `titleResId: Int = 0` was removed from all 26 enums, so the
compiler now rejects a preference key declared without a title. That is the part worth keeping: the
trap cannot come back.

#### A sentinel that had been waiting to bite

`SearchableItem.Wiki` carries a page title that comes from the ReadTheDocs API, so it is plain text,
not a resource. It was stored as `titleResId = 0`, and `SearchIndexBuilder.safeGetString()` maps `0`
to `""` - which would make every wiki hit unfindable by its own title.

It never fired. Wiki results are a live search, not part of the built index: `WikiSearchRepository`
fills `SearchIndexEntry` itself, with `localizedTitle = title`, and never calls
`createIndexEntry()`.
So the `0` sat there unread, correct only by the accident that nothing looked at it. It is now
`TextRef.Literal(wikiTitle)`, and the snippet is the summary, which is what both fields meant all
along. The point is not a fixed bug - it is that the sentinel could not survive the type change,
whereas an `Int` field will hold a lie indefinitely.

#### Deliberately left for later

`IntPreferenceKey.entries: Map<Int, Int>` and `resolvedEntries: Map<Int, String>?` are the same
resource-id-or-string pair that `TextRef` exists to collapse, and collapsing them would delete
`IntKeyWithEntries` and `withEntries` entirely. It is not in phase 1 because it touches plugin call
sites, and phase 1 was scoped to `:core:*`.

`UnitType.valueResId()` / `rangeResId()` stay `Int?` for now. They are format templates that need
arguments supplied at the call site. (They lived in `:core:keys`, not `:core:ui` as an earlier
version of this note said. Wave 11 moved them.)

### Wave 11 - `TextRef` phase 2, and `:core:keys` owns only its own strings (done)

Committed as `95da0fa7ca` and `6fdb924e6b`. Three separate jobs that together leave `:core:keys`
with no raw Android resource id in its API and nothing in it that other modules reach into.

#### The entries maps

`IntPreferenceKey.entries: Map<Int, Int>` and `StringPreferenceKey.entries: Map<String, Int>` became
`Map<K, TextRef>`. The 100 `value to R.string.x` pairs did **not** change: the same trick as
`title` - the constructor parameter turned private (`entriesResIds`) and a computed property exposes
it. 11 enums, 17 named arguments.

`resolvedEntries` is **deleted**. It was the parallel `Map<K, String>?` that took precedence over
`entries`, i.e. exactly the resource-id-or-string pair `TextRef` exists to collapse. `withEntries`
now takes `Map<K, TextRef>`, so callers choose `Res` or `Literal` per entry. Two read sites in
`AdaptivePreferenceItem` collapsed to one each.

That change is what made a real fix possible: `EopatchPumpPlugin` built its reminder labels as
`"$it U"` and `"$it hr"` - string concatenation no translator can reach. They now use the existing
translated templates `units_format_insulin_int` and `units_format_hours`. Note the visible
consequence: expiration entries read "1 h", not "1 hr". Had `withEntries` kept `Map<K, String>` and
wrapped in `Literal` internally, the bug would have been frozen in place.

#### UnitType split

`UnitType.kt` is now the enum plus `decimalPlaces()` and `step()` - zero resources, zero Android. The
resource mapping moved to `core/ui/.../UnitTypeText.kt` as `unitLabel(): TextRef?`,
`rangeText(value, min, max): TextRef?` (arguments taken here, so a bare template cannot reach the
screen) and `valueFormatResId(): Int?`, which stays an `Int` because the slider applies it to
whatever value the user drags to.

#### The unitLabel pair, and an identity check on a resource id

`formatSliderDisplayValue` took **both** `unitLabelResId: Int = 0` **and** `unitLabel: String = ""`.
One `unitLabel: TextRef?` replaced both, killing the sentinel and the pair, across ~26 files.

Worse than the sentinel: three places detected a semantic concept by comparing resource ids -
`unitLabelResId == KeysR.string.units_min` in `FormatUtils`, `SliderWithButtons` and
`ValueInputDialog`. All three are now an explicit `asDuration: Boolean`. That kind of check is
exactly what breaks when ids stop being AAPT ids.

#### `:core:keys` stops hosting other people's strings

40 `units_*` strings moved to `:core:ui`, where 99 of their references already were - only **two**
were used from inside `:core:keys` (`units_mgdl`, `units_mmol` in `StringKey.GeneralUnits`), and they
became `TextRef.Literal("mg/dL")` / `TextRef.Literal("mmol/L")`, which is honest because they read
the same in every locale. `prefs_range_title` moved too (external-only).

**The translations were moved by hand, per locale, in the same commit.** Not left to a later Crowdin
sync - see wave 12 for why that matters.

Side effect worth knowing: after this move `:core:keys` has **zero format placeholders**, zero
plurals and zero string-arrays. Those are the categories that are hard build errors in
compose-resources, so the module that is about to convert is now the clean one.

#### Deleted as dead

`getDependingOn` had **no callers** anywhere - dependency visibility is computed reactively from
`preferenceKey.dependency` in `PreferenceState.kt`. Removed from the interface, both
`PreferencesImpl`s, the `PreviewUtils` stub and its one test.

`rangeText` was **not** deleted, and the reasoning is worth recording. It looked dead - grep for
`min = Int.MIN_VALUE` finds nothing. But seven pump enums **default** `min`/`max` to the extreme
values, and three Insight constants take the default, so `hasValidRange` is false for them and the
branch runs. It returns null today only because those keys have no `unitType`. Same grep-the-explicit-
form mistake as wave 3.

### Wave 12 - clearing the way for compose-resources (done, not yet converted)

Two preparatory stages, both green, both on device.

**Stage 1 - `TextRef.Res` renamed to `TextRef.AndroidRes`** across 187 sites. Pure rename. It frees
the name `Res` for the compose-resources form and, more importantly, states the truth: an Android
resource id is *one* kind of text reference, not the only one. Pump drivers keep AAPT resources
permanently, so two variants is the end state, not scaffolding.

**Stage 2a - the `@StringRes Int` APIs take `TextRef`.** Five `:core:keys` strings were read as
Android ints by three other modules through APIs that cannot take a `StringResource`. Rather than
duplicate those strings (which would mean deleting them from Crowdin later - see wave 12's warning),
the APIs changed:

| API                              | Change                                                            |
|----------------------------------|-------------------------------------------------------------------|
| `SWItem.label`                   | `Int?` -> `TextRef?`; `label(Int)` delegates                      |
| `SWScreen.header`                | `Int = 0` sentinel -> `TextRef?`; `with(Int)` delegates           |
| `SWEventListener.textLabel`      | `Int = 0` sentinel -> `TextRef?`                                  |
| `NumberInputRow.labelResId`      | -> `labelRef: TextRef?`, `Int` overload keeps **79** call sites   |
| `PasswordCheck.queryPassword` / `setPassword` | `TextRef` overloads; `Int` versions delegate         |

The delegating-overload trick is why this was ~5 call-site changes and not ~70. External callers now
ask the key for its own label - `StringKey.ProtectionMasterPassword.title` instead of
`R.string.master_password` - which also deleted a genuine pre-existing duplicate (`master_password`
and `pref_title_master_password` were both "Master password").

**Result: no module outside `:core:keys` references its `R` class.** That was the precondition.

> **A recurring mistake, recorded so it stops happening.** Three times this wave I grepped for one
> syntactic form and concluded something was absent: `titleResId = 0` (missed the constructor
> *default*), `^import android` (missed fully-qualified and auto-imported use), `min = Int.MIN_VALUE`
> (missed the default again). Two of those nearly deleted live code. "I found no matches" is weak
> evidence, especially before a deletion.

### Wave 13 - what the compose-resources spike proved

A throwaway standalone project (not in the repo) answered the questions that decide the conversion.

| Question | Answer |
|---|---|
| Does CMP resolve on Kotlin 2.4.10 / Gradle 9.6.1? | **Yes** - plugin `1.11.1`, `BUILD SUCCESSFUL`, generated a public `Res` |
| Do Android-style locale folders survive? | **Yes** - `values-de-rDE`, `values-pt-rBR`, `values-zh-rCN`, `values-iw-rIL` all parse into `LanguageQualifier` + `RegionQualifier`. **No renaming needed.** |
| Does `mingwX64` work? | **No.** `components-resources:1.11.1` publishes Native only for `ios_arm64`, `ios_simulator_arm64`, `macos_arm64` |
| Build cost? | baseline **15.8s**, +compose plugin **17.7s**, +328 strings x 31 locales **29.3s** |

The build-cost split matters: the **Compose compiler plugin is ~2s**; the other **~11.6s is resource
codegen**, which scales with the number of locales, not with code. My worry that the IR transform
over the enums would be expensive was wrong.

The `mingwX64` answer only matters because it was our Windows-buildable proxy for "compiles to
Native". The **real** target set is Android + iOS + JVM-on-Windows, and compose-resources supports
all three. So `mingwX64` is scaffolding we can drop for this module; the cost is that its Native side
can then only be compiled on macOS.

Two corrections to earlier reasoning in this note:

- ~~`ResourceEnvironment`'s constructor is **public** in 1.11.1~~ - **this was wrong, and it was the
  load bearing claim.** Wave 14 downloaded the published `components-resources:1.11.1` sources jar
  and read it: the declaration is `class ResourceEnvironment internal constructor(...)`. The only
  public producer is `getSystemResourceEnvironment()`, which reads `Locale.getDefault()`. So there is
  still **no way to ask for a specific locale**, and the always English search index still cannot be
  built. Wave 10's original objection stood the whole time. 1.12.0-beta03 does not fix it either -
  the constructor is still internal and has gained a fifth parameter.
- Holding a `StringResource` costs **kotlin-stdlib only** - `components-resources` declares nothing
  else in `apiElements`; Compose appears only in `runtimeElements`. (Still true.)

Still true: **every `getString` overload is `suspend`.** There is no synchronous variant. Wave 14
adds one more: `stringResource()` is a **blocking** read on every platform except JS, including
inside composition.

**The spike also asked the wrong question about locale folders.** It checked that `values-de-rDE`
*parses* into a `LanguageQualifier` plus a `RegionQualifier`. It never checked that such a folder
*matches* a request. It does not, when the request carries no region - see wave 14.

There is real precedent on this exact toolchain: **Meshtastic-Android** runs a dedicated
`:core:resources` module (1747 strings, ~40 locales, `publicResClass = true`) on AGP 9.3.1 /
Kotlin 2.4.10 / CMP 1.11.1, and **Todometer-KMP** targets android + jvm + iosArm64 +
iosSimulatorArm64. Both build iOS only on `macos-latest` runners - which is the answer for a
maintainer with no Mac, and free for a public repo.

### Wave 14 - `:core:keys` is multiplatform, and the string plan changed (done)

Committed as `c8c1069817`, `29dd6ff33e` and `eeddd8182c`. This wave overturned the destination that
waves 10 to 13 had been walking towards, so the reasoning matters more than the diff.

#### compose-resources cannot serve this app, and no version of it can

Two facts, both read out of the **published** `components-resources:1.11.1` sources jar rather than a
docs page or a GitHub branch:

- `class ResourceEnvironment internal constructor(...)`. The only public producer is
  `getSystemResourceEnvironment()`, which reads `Locale.getDefault()`. There is no way to ask for
  English while the UI is in another language. Wave 13 claimed this had been fixed; it had not, and
  1.12.0-beta03 still has it internal, now with a fifth parameter.
- `filterByLocale` has exactly three steps, and the source comment says so: exact language+region,
  then language **with no region qualifier**, then no locale qualifiers at all. There is no
  sibling-region fallback.

That second one is the fatal one, and nobody had looked at it. `LocaleHelper.currentLocale()` builds
a **region-less** `Locale` for 22 of the 25 in-app languages, and every `:core:keys` folder is
region-pinned (`values-cs-rCZ`; no bare `values-cs` exists anywhere). Walk the algorithm: step 1
needs a region and there is none, step 2 needs a folder without a region and there is none, so it
falls through to the unqualified default - **English**. Concretely, 8 of the 11 translated locales
would have shown English preference screens: bg, cs, es, fr, it, nb, ro, sk. It also breaks device
locales whose region differs from the folder - de_AT, fr_CA, es_MX, nl_BE, zh_HK - which AAPT
resolves correctly today.

Build green, no crash, no test catches it, and CI runs no tests. This is exactly the failure shape
section 9a describes, and it would have been introduced deliberately.

#### moko-resources was the better library and still lost

It does no locale matching of its own: it generates real `values-XX` files and emits
`StringResource(R.string.key)`, so Android keeps AAPT semantics, and `StringDesc.LocaleType.Custom`
is a real locale override. Both defects gone. But `0.26.4` shipped 2026-05-06, one month **before**
Kotlin 2.4.0, with no commits since, 175 open issues and a catalog pinned to Kotlin 2.1.0/2.3.20. A
medical app's string layer is the wrong place for an unattended dependency.

#### What was built instead

The insight is that the module shape was never the blocker. `:core:data` has been multiplatform since
wave 5 and 39 modules consume it with a plain `implementation(project(...))`. What blocked
`:core:keys` was the `R.string` **`Int`** inside 6 enum files - a *content* problem.

`buildSrc/src/main/kotlin/GenerateKeyStringsTask.kt` makes one pass over `res/values/strings.xml` and
emits two files:

| Generated | Where | What |
|-----------|-------|------|
| `KeysStrings` | `commonMain` | 327 `val x: TextRef = TextRef.Named("x")` - no Android types |
| `KeysStringIds` | `androidMain` | 327 `"x" to R.string.x`, plus `idOf(name)` |

Same pass, so they cannot drift: a string deleted from the XML disappears from both and any call site
naming it stops compiling. `TextRef` gained `Named(name, args)`; the 6 enums now take
`override val title: TextRef` directly and the computed `AndroidRes` properties are gone; 342
references were rewritten; the three resolvers gained a `Named` branch.

The KDoc in `TextRef.kt` that argued against names was **conditionally right and absolutely wrong**.
It said a name needs `Resources.getIdentifier()` - reflective, invisible to R8, silently 0 for a
typo. All three objections die against a *generated* map: no reflection, R8 sees 327 literal
`R.string.x` references, and a typo does not compile.

Net effect: **zero resource files moved, `crowdin.yml` untouched, AAPT still resolves every locale,
and `gsNotLocalised` still works.** Only one of the 19 production `gsNotLocalised` call sites touches
`:core:keys` at all - `SearchIndexBuilder.kt:340`. The other 18 resolve `:plugins:sync` and
plugin-name strings and were never at risk.

#### The module flip, and three things it taught

`com.android.kotlin.multiplatform.library`, `android { }` **inside** `kotlin { }`,
`androidResources { enable = true }`, targets android + jvm + iosArm64 + iosSimulatorArm64. Sources
went to `commonMain` (47), `androidMain` (res), `androidHostTest` (1 test); git recorded every one as
a pure rename.

- **The flavour fear was unfounded.** That AGP extension really does expose no `productFlavors`, but
  AGP matches a flavourless library to any app variant. All 5 flavours of `:app` and `:wear` build.
- **`platform()` does not exist** in a Kotlin source set dependency block. Use
  `project.dependencies.platform(...)`.
- **Dropping `android-module-dependencies` silently switches `MissingTranslation` on** and restores
  `checkReleaseBuilds` to its default `true`. With 19 empty locales that could fail a release build,
  so the `lint { }` block has to be restated in the module.

#### iOS compiles here

Kotlin/Native has cross compiled klibs for Apple targets from any host since 2.2.20, on by default -
`PropertiesProvider.kt:595` reads `... ?: true`. `compileKotlinIosArm64` and
`compileKotlinIosSimulatorArm64` genuinely **execute** on the Windows machine this project is
developed on and produce real klibs, against the 177 prebuilt `ios_arm64` klibs already sitting in
`~/.konan`. Nothing is fetched from Apple and Xcode is not involved.

A Mac is still needed to **run** iOS tests, to link frameworks and XCFrameworks, and for cinterop.
Those tasks are `enabled = false` and report **SKIPPED**, not failed - so a build stays green even if
the entire Apple side silently stops compiling. Any CI job must assert the task *executed*.

#### Verified

`:app` + `:wear` green across all 5 flavours; `testFullDebugUnitTest allTests` green; the resource
baseline is byte-identical before and after the move (3924 elements, 31 dirs, 12/12 name lists); and
on an emulator the English screens render titles and summaries, Czech renders `:core:keys` strings in
Czech, and Danish renders Danish.

`runtests.bat` / `.sh` now run `testFullDebugUnitTest allTests`. Without `allTests` a multiplatform
module has no `testFullDebugUnitTest` task and silently runs **no tests at all** - which is what had
been happening to `:core:data` and `:core:nssdk` since waves 5 to 9, unnoticed because
`failOnNoDiscoveredTests = false`.

### Wave 15 - the data spine builds for iOS, and 15 tests run on Native (done)

Committed as `a4291a17cc`, `01678c46dc` and `47798f9e84`.

`:core:data` and `:core:nssdk` gained `iosArm64()` and `iosSimulatorArm64()`. Both produce genuine
klibs - `native_targets=ios_arm64` and `ios_simulator_arm64` in the manifests - built on Windows.

**The whole shared data spine needed exactly four `actual`s**, which is the real headline. Grep all
three multiplatform modules for `expect` and that is the entire platform surface underneath a
complete Nightscout read/write client:

| `expect` | iOS `actual` |
|----------|--------------|
| `systemUtcOffsetAt` | `NSTimeZone.localTimeZone.secondsFromGMTForDate` - the direct counterpart of `TimeZone.getOffset(timestamp)`, per-moment so DST is right |
| `NumberFormatPlatform` | `NSNumberFormatter` - same CLDR data as `DecimalFormat`, with grouping off, half-even rounding and an explicit separator |
| `nsHttpClient` | Ktor **Darwin**, i.e. `NSURLSession` - the system's own connections, proxies and certificate validation |
| `nsIoDispatcher` | `Dispatchers.IO`, which Kotlin/Native does provide on Apple targets |

These are real implementations, not stubs, and they compile here because the Apple platform klibs
ship **pre-generated inside the Windows Kotlin/Native distribution** - 177 of them for `ios_arm64`,
including `Foundation`. Running cinterop against a new header needs a Mac; consuming what is already
generated does not.

**`mingwX64` stays, and the reason is the opposite of what was assumed.** It is obsolete as a
*compile* proxy now that the real targets build. But it is the only Kotlin/Native target whose tests
can *run* on Windows - KGP says so plainly: *"Native task 'iosSimulatorArm64Test' is disabled ...
cannot run on the current host (windows-x86_64). Reason: simulator tests require macOS."*

So `:core:data` got a `commonTest`. `ICfgTest` (8) and `SourceSensorExtensionsTest` (7) moved there
from `jvmTest` and were rewritten from Truth + JUnit 5 to `kotlin.test`; `NumberFormatTest` stayed on
the JVM because it *is* the oracle - it compares against `java.text.DecimalFormat`. Result: **15 tests
now execute through Kotlin/Native**, and the same 15 still run on the JVM. That matters more than it
sounds for `ICfgTest`, which is dosing arithmetic: the point of sharing it with a client is that it
computes the same numbers on every platform, and until now nothing checked that off the JVM.

Two build details worth keeping:

- The `ExperimentalNativeApi` opt-in lived inside the `mingwX64 { }` block. It has to move to
  `targets.withType<KotlinNativeTarget>`, or `ICfg.iobCalcForTreatment`'s `assert()` will not compile
  for iOS.
- `getByName("iosMain")` fails with *"KotlinSourceSet with name 'iosMain' not found"*. The default
  hierarchy template creates it **after** the build script is evaluated, so use the lazy `iosMain { }`
  accessor.

**The one thing this could not prove was whether `NSNumberFormatter` and `NSTimeZone` actually agree
with `DecimalFormat` and `TimeZone.getOffset`** - which matters, because `utcOffset` is stored in
every record, takes part in `contentEqualsTo` and is validated by Nightscout, so a units mistake
would corrupt data quietly rather than crash. That gap is closed in wave 16.

### Wave 16 - the shared code runs on Apple, and the actuals are correct (done)

Committed as `e115e407a7`, `08f5384826` and `c017e469b4`.

Two `commonTest` suites were added to `:core:data` to pin platform behaviour as literal expectations
rather than as a comparison, so they mean something on a target that has no JVM to compare against:
`NumberFormatParityTest` (10 tests) and `SystemTimeZoneTest` (4).

**They found a real divergence on their first run, on Windows.** `0.35` is not representable as a
double - the nearest one is `0.34999999999999997779...`, just below the midpoint. `DecimalFormat`
works from that true decimal value and rounds **down** to `0.3`. The mingw actual multiplies by ten
first, and `0.35 * 10.0 == 3.5` exactly in IEEE-754, so it sees a perfect tie, applies half-even, and
produces `0.4`. Ties are now pinned only on exactly representable values, where every platform must
agree; the non-representable case is documented instead. `DecimalFormat` is the reference, because it
is what every existing AAPS number has been rendered with.

`.github/workflows/ios-ci.yml` runs on `macos-latest`, needs no secrets, and asserts the tasks
**executed** rather than that the build was green - a disabled Kotlin/Native task reports SKIPPED and
still exits 0, so "BUILD SUCCESSFUL" on its own would stay green if the whole Apple side silently
stopped building. It also checks each klib manifest names the target it claims.

Result: **29 tests execute on the iOS simulator, zero failures** - the same 29 that run on JVM and
mingw - and all six klibs build on macOS.

The first run passed but proved less than it looked: GitHub's macOS runners are **UTC**, where every
assertion in `SystemTimeZoneTest` holds trivially because the offset is zero in any unit. The job now
pins `TZ=Europe/Prague`, which has both a non-zero offset and daylight saving. There the winter offset
is 3,600,000 ms, so a seconds-returning implementation would fail both the whole-minutes check
(`3600 % 60000 != 0`) and the whole-hour DST delta. It passes, which is what actually confirms the
`* 1000` in the `NSTimeZone` actual.

Two traps worth remembering from writing that workflow:

- GitHub Actions runs `bash -e` but **not** `pipefail`, so `./gradlew ... | tee log` returns *tee's*
  exit code and a failed build reports success. Same trap as the pipe warning further down, in a new
  place.
- Kotlin/Native rejects a **comma** inside a backtick test name, which the JVM accepts.

### Was behaviour preserved?

An audit ran five parallel agents against the migrated code, each trying to find an input where old
and new differ, with a second pass trying to refute what they found. Results worth keeping:

- **`DecimalFormat` -> `NumberFormat`: identical.** 17 patterns against ~90 values across 57
  locales,
  then all 17 against every `Locale.getAvailableLocales()` (~800). Zero differences, including NaN,
  infinities, -0.0, 1e300 and the half-even boundaries.
- **`TimeUnit` -> `kotlin.time`: identical where it can be reached.** Checked by loading the real
  `kotlin-stdlib-2.4.10.jar` and comparing by reflection over 42 million values. They diverge only
  past about 146 million years, which no call site can reach. All 93 changed lines were checked
  individually for operator precedence, including the `?:` re-parenthesising in `TreatmentMapper`
  and the `hours.hours` local-shadowing in `PatchManager`.
- **The automation `DecimalFormat("1")` change really is invisible.** `:plugins:automation` has
  neither Gson nor kotlinx.serialization, `Trigger.toJSON()` is hand written and emits only
  value/comparator/units, and the reflection in `ActionEditors.kt` reads only `"text"`, `"daysBack"`
  and friends. The field is written and never read.

Three real changes, all of them fixes, and one bug that the audit caught and that is now fixed (the
formatter cache, above):

| Change                              | Effect                                                                     |
|-------------------------------------|----------------------------------------------------------------------------|
| `qs()` no longer groups             | `1234.5` with 1 digit: en `1,234.5` -> `1234.5`, de `1.234.5` -> `1234.5`  |
| `formatUS` uses `Locale.US` symbols | correct in Arabic; also swaps U+2212 for `-` on sv, nb, lt, hr, fi, sl, et |
| automation `%d` patterns            | the literal-digit bug is gone, but nothing renders it                      |

`qs()` also gained a `max(0, digits)` guard: `DecimalFormat` used to clamp a negative
`maximumFractionDigits` to 0, while `NumberFormat` throws. No caller passes a negative, but the
guard
keeps the old contract on a public interface method.

### Wave 17 - what the in-repo CMP spike proved, and its recipe (spike now deleted)

Wave 13's spike was a standalone project outside the repo and only asked about **compose-resources**.
A second spike, `:spike:cmp`, lived *inside* the build for several waves and asked a different
question: does Compose Multiplatform work in **this** build, next to the androidx Compose the app
already uses. It has been deleted now that it has nothing left to answer, so its result and its build
recipe are recorded here.

**What it proved.** Two real `:core:ui` files - `PlusMinusEdit` and its `Helpers` - compiled unchanged
from `commonMain` for `android`, `iosArm64` and `iosSimulatorArm64`, against the real
`:core:data` and `:core:keys` (not stubs), with `stringResource(TextRef)` as an `expect`/`actual`
seam. That is the whole `:core:ui` module flip in miniature, so the flip is a build-configuration
problem rather than an open technical question.

**The recipe, which the flip should reuse:**

```kotlin
plugins {
    kotlin("multiplatform")
    alias(libs.plugins.android.kmp.library)   // NOT com.android.library - AGP 9 refuses that with KMP
    alias(libs.plugins.compose.compiler)      // ships with Kotlin; org.jetbrains.compose hard-fails without it
    alias(libs.plugins.compose.multiplatform)
}
```

Four deliberate omissions, each of which would have cost a debugging session to rediscover:

- **No `compose.components.resources`.** compose-resources was rejected for AAPS (it cannot match a
  region-less locale against a region-pinned folder). `generateResClass` defaults to `auto`, which
  generates nothing unless that dependency is present - so leaving it out *is* the opt-out.
- **No `jvm()` target.** It pulls in the desktop Compose surface (skiko-awt) and gives the module
  another way to fail without saying anything about iOS.
- **No `androidResources`.** Off by default for a KMP library. `:core:ui` *does* own `res/`, so unlike
  the spike it must enable it - and that is exactly where CMP-9547 (resources not packaged under
  AGP 9) becomes relevant, which it could not be for the spike.
- **`lint { checkReleaseBuilds = false }` restated inline**, because `android-module-dependencies`
  applies `com.android.library` and so cannot be applied to a multiplatform module. Same reason as
  `:core:keys`.

`material-icons-extended` needed its CMP artifact (`libs.cmp.material.icons.extended`): both copied
files use `Icons.Filled.Remove`, which is not in the core icon set.

The general form of the question was already answered in public - coil-kt/coil and plainhub/plain-app
both ship these four plugins on Kotlin 2.4.10 + AGP 9.3.1 + CMP 1.11.1 - so the spike only ever
checked that nothing repo-specific interferes. Nothing did.

---

## 9. Open decisions

1. ~~Add `:core:utils` -> `:core:data`?~~ **Decided: no.** It would slow the build down for no real
   gain, and `:core:utils` is not a KMP candidate. `StringUtil` was fixed in place instead, with
   `Locale.US` symbols and no new dependency.
2. ~~Add grouping support to `NumberFormat`?~~ **Decided: not needed.** The grouping in
   `DateUtilImpl.qs()` was accidental and broken, so it was removed rather than reproduced.
3. ~~Wave 2 size?~~ **Decided: convert `TimeUnit` only, leave `T` alone.** `TimeUnit` is a real
   blocker and had to go. `T` is not, so replacing it would have been style work paid for with about
   221 build warnings. The dead `T.now()` was removed instead, which is a real cleanup with no cost.
4. ~~Use `DateUtil` instead of `System.currentTimeMillis()` everywhere?~~ **Decided: only where it
   is
   needed.** The principle is right - `DateUtil` is an interface, so it mocks in tests and can
   differ
   per platform - but the sweep would be **547 sites in 227 files**, plus 62 `TimeZone.getDefault()`
   in 57 files, and most of it sits in pump drivers that will never be KMP. Do it in the modules on
   the KMP path, leave `:pump:*` and `:wear` alone, and make it a rule for new code so the number
   stops growing.
5. ~~Which branch for the first KMP module?~~ **Decided: `kmp/core-data-experiment`, and it worked.
   **
   The fear was that converting `:core:data` to `kotlin("multiplatform")` would change how Gradle
   resolves it for the 13 dependent modules. It does not - a multiplatform module still publishes a
   normal Android variant, and all 13 built unmodified. The branch was meant to be thrown away and
   is
   now worth merging.
6. **Still open: `wear/SmallestDoubleString.kt`** - the last `DecimalFormat` in a non-test file that
   is not the deliberate platform seam. It builds patterns from a runtime string, so it needs real
   logic rather than a mapping.
7. ~~The `:core:nssdk` converter switch.~~ **Done - see wave 7.** The joda parsing turned out **not
   **
   to be part of the contract: it is a plain helper on `RemoteTreatment`, called after decoding, so
   it
   moves on its own schedule. The ~8 non-null fields were the real answer to this question, and the
   answer was "give them defaults" - see the trap in wave 7. `RemoteStatusResponse` was left strict
   on
   purpose: `v3/status` is AAPS's own call to a server it just authenticated against, and a reply
   missing those fields is not something to carry on from.
8. ~~Retrofit converter, or straight to Ktor?~~ **Decided: the converter first, and the reasoning in
   the earlier version of this note was wrong.** It argued that Ktor's failure mode is louder, but
   going straight to Ktor does not *replace* the serialization change, it *bundles* it - you get the
   quiet-wrong-data risk anyway, plus transport risk, in one commit. Separating them meant the
   characterization tests could actually do their job on the serialization half. The throwaway
   dependency cost nothing in the end: Retrofit 3.0.0 ships an official
   `converter-kotlinx-serialization`, so it was one catalog line and no third party code.
9. ~~The OkHttp disk cache needs a `Context`.~~ **Resolved: there is no cache.** It existed only so
   a
   revalidated GET could surface as a 304, which the paging workers used as their stop condition.
   That is now expressed directly - stop when the cursor cannot advance - so the cache, the
   `Context`
   and the two `Cache` instances that shared one directory are all gone. See wave 8.
10. ~~R8 has never run.~~ **Not a risk: AAPS does not minify.** Both convention plugins
    (`android-app-dependencies` and `android-module-dependencies`) set `isMinifyEnabled = false` for
    the `release` build type, so R8 never shrinks or obfuscates and the usual
    "kotlinx.serialization needs keep rules" problem cannot occur here. A release build was run
    anyway: it succeeds, and all five generated `$$serializer` classes plus the Retrofit kotlinx
    converter are present in the release dex. The `benchmark` variant (`initWith(release)` plus
    debug
    signing) installs and starts clean; its runtime sync check is still outstanding because the
    emulator lost DNS, which starved both apps equally.
11. **Still open: merge `kmp/core-data-experiment` into `dev`.** Waves 5 to 9 all live there.
    Nothing
    argues against it any more - the branch was device-verified in mixed-version pairs at each
    step -
    but it is a real merge of a wire format change and deserves its own decision. It gets riskier
    the
    longer it waits: `dev` has not moved yet, so the merge is still a fast-forward.
12. **Still open: client-control crypto on a non-JVM platform.** It sits in `jvmMain` and nothing in
    `commonMain` calls it, so no target needs it today. A **follower** never signs commands or
    unwraps pairing offers, so this only becomes real when another platform should *send* commands.
    Golden vectors are ready; the choice is then `expect` / `actual` with a hand written Apple
    implementation, or cryptography-kotlin. Prefer deciding it when the platform exists, not before.
13. **Still open: does web ever matter?** It is the one target that changes the **API shape** rather
    than just the implementation - WebCrypto is async-only, so `sign()` and `wrap()` would have to
    become `suspend`. Cheap to decide now, expensive to retrofit.
14. ~~compose-resources, moko-resources, or something else for strings?~~ **Reopened and decided
    again in wave 14: neither. Android keeps AAPT, and only a platform neutral *handle* is shared.**
    compose-resources is not the destination after all - it cannot match a region-less locale against
    a region-pinned folder, and it has no locale override, so it would have broken 8 of the 11
    translated locales and the always English search index at the same time. moko-resources solves
    both, but its latest release predates Kotlin 2.4 with no commits since. `TextRef` is still the
    seam, and it is now what makes the answer cheap: the backend is hidden from 342 call sites.
15. ~~Collapse `IntPreferenceKey.entries` / `resolvedEntries`.~~ **Done in wave 11.** `entries` is
    `Map<K, TextRef>`, `resolvedEntries` is deleted, `withEntries` takes `TextRef` - which is what
    let the Eopatch `"$it U"` concatenation become a translated template.
16. ~~`RileyLinkStringPreferenceKey.MacAddress` should be a `NonPreferenceKey`.~~ **Done - moved to
    `RileyLinkStringKey`.** It is storage, not a preference: no title, no screen, written by the
    pairing wizard. With it gone the `titleResId = 0` default came out of all 26 enums, so a
    preference key without a title no longer compiles. See wave 10.
17. ~~Does `:core:keys` need its own strings?~~ **Yes, structurally.** It is a leaf module - zero
    project dependencies - and `:core:ui` depends on **it**. So the strings cannot move to `:core:ui`
    (cycle), and a key naming its own title at compile time means they must live in the same module.
    What changed in wave 14 is only *how* they are named: the strings stayed exactly where they were,
    in `res/values*`, and the module became multiplatform around them.
18. ~~compose-resources on `:core:keys`, or negative-integer tokens in `TextRef`?~~
    **Overturned in wave 14. Neither: `TextRef.Named` plus a generated id map.** The conclusion that
    tokens were wrong survives - they encode a handle without providing a table behind it - but
    compose-resources turned out to be worse, not better. A *generated* name is what the reasoning
    missed: it is not the reflective `getIdentifier()` lookup that the token scheme was invented to
    avoid, and it needs no resource framework at all on the platform that already has one.
19. **Still open: when to enable `MissingTranslation` lint.** It is disabled repo-wide and is the
    reason 19 languages silently lost `:core:keys`. Wave 14 makes this less urgent but not moot: the
    generator now prints per locale completeness on every build of `:core:keys`, so that one module
    is covered. The other 32 string owning modules still have no detector.
20. **Still open: what comes after `:core:keys`.** It holds 1063 strings and every module depends on
    it, so the string question returns at a larger scale - though wave 14's answer scales with it,
    because nothing has to move. The bigger question for `:core:ui` is not strings at all: it is 16
    files importing `android.*` and a handful of non-Compose `androidx` artifacts.
21. ~~Do Apple targets need a Mac to compile?~~ **No - decided in wave 14.** Kotlin/Native has cross
    compiled klibs for Apple targets from any host since 2.2.20, on by default. `:core:keys` compiles
    for `iosArm64` and `iosSimulatorArm64` on the Windows machine this project is developed on. A Mac
    is still needed to *run* iOS tests, to link frameworks, and for cinterop.

Waves 1 to 4 are committed on `dev`. Waves 5 to 14 are committed on `kmp` (HEAD `eeddd8182c`), which
is now **20 ahead and 1 behind `dev`**. It stopped being a fast-forward when `dev` moved, so the
merge in open decision 11 is a real merge and gets no cheaper by waiting.

Waves 5 to 9 were each verified against a live Nightscout before the next started - waves 5 and 6 on
WSA, waves 7 to 9 on an emulator running a new master against a **pre-KMP client**, so every step was
checked in a mixed-version pair. Waves 10 to 12 are compile-time refactors with no wire format in
them, and were verified on the emulator by reading the UI: preference screens, nested sections,
search, the client's sync badges, twelve setup-wizard screens, and the treatment dialogs. The failure
mode of these waves is a **blank label**, not a crash, so they need eyes rather than logcat.

Still unverified at runtime, because they need hardware the emulator cannot provide: the Eopatch and
Medtrum entry lists, and the Insight `rangeText` path.

The `FoodManagement` comma defect in section 10 is found but not fixed.

---

## 9a. LIVE BUG - `:core:keys` translations are missing in 19 of 30 languages

**This is on `dev` today, it predates all KMP work, and nothing in the build can see it.**

> **A second, separate translation bug was found while checking this one, and is now fixed**
> (`c8c1069817`). The in-app language list offered `"dk"` for Danish, but `dk` is a country code -
> the ISO 639 language code is `da`, and all 32 modules keep their Danish text in `values-da-rDK`.
> No `values-dk` folder exists anywhere in the repository, so `Locale.Builder().setLanguage("dk")`
> matched nothing and **picking Danish rendered the entire app in English**. The translations were
> there the whole time and simply unreachable. Fixed at the single choke point,
> `LocaleHelper.currentLocale()`, which now maps `dk` to `da`; the stored preference value is left
> alone, because `GeneralLanguage` syncs between master and client. Device-verified.
>
> Two more offered languages have no folders anywhere - `af` (Afrikaans) and `ga` (Irish) - but
> nothing is lost there, because they were never translated. The picker simply promises more than it
> can deliver.

`core/keys/src/main/res/values/strings.xml` has 327 strings. Only **11 locales** have translations:
bg, cs, es, fr, it, nb, ro, sk, vi, zh-rCN, zh-rTW. The other **19 locale files exist but contain
zero `<string>` elements**: de, ru, pl, nl, pt-rBR, pt-rPT, tr, sv, uk, ar, ca, da, el, hr, hu, iw,
ko, lt, sr.

The proof it is a regression, not merely untranslated work:
`core/ui/src/main/res/values-de-rDE/strings.xml` still carries the marker
`<!-- master_password moved to core/keys/strings.xml -->`. The German text was removed from
`:core:ui` when the string moved, and never arrived. German users see these strings in English.

**Why nothing caught it:** `buildSrc/.../android-module-dependencies.gradle.kts:30-31` does
`disable += "MissingTranslation"` and `disable += "ExtraTranslation"` for **every** Android library,
and CI runs only `:app:assemble` / `:wear:assemble` - no lint, no tests. There is currently no
mechanism in this repo that can notice a lost string.

### What was already done about it

A Crowdin **TM pre-translate** was run against `fileId 5662` (`/dev/core/keys/.../strings.xml`) for
all 19 languages, with `translateUntranslatedOnly: true` and `autoApproveOption: "none"`.

| | before | after |
|---|---|---|
| de | 18 | 150 |
| ru | 40 | 176 |
| pl / tr | 0 | 150 |
| nl | 11 | 157 |
| ... | | typically 100-150 of 327 |

**Recovery is partial (~40%), and this tells us something.** If all these strings had simply *moved*,
TM would have matched nearly all of them. It did not - so roughly 60% were **never translated in any
locale**. It is two problems: a genuine relocation loss, sitting on a larger backlog of new strings
from the Compose preferences migration.

**Nothing is approved.** The project exports approved translations only, so a `download` still
returns empty files until someone approves in the web editor. The entries are also fuzzy matches
(`translateWithPerfectMatchOnly: false`), which is why they were left for review.

### Why this blocks the string move

Phase 3 relocates 327 strings x 30 locales. Right now you **cannot tell a relocation failure from
the pre-existing gap** - for 19 languages there is nothing left to break. Fix and verify first, then
move. Concretely, before converting:

1. Approve the pre-translated suggestions (or decide to discard them).
2. Turn `MissingTranslation` back on - **warning level plus a CI report**, not an error gate, or the
   first run is unusable.
3. Re-download and commit, so the repo has a known-good baseline.

Useful commands are in the session notes: the CLI needs `-b dev`, a minimal temp `crowdin.yml` with
`preserve_hierarchy: true`, and `--base-path`. The API route avoids the export build entirely:
`GET /api/v2/projects/309752/languages/{lang}/translations?fileId=5662`.

---

## 9b. ~~Where to resume - `:core:keys` to compose-resources~~ Superseded by wave 14

**The recipe that used to be here would not have built.** It applied `com.android.library` next to
`kotlin("multiplatform")`, which AGP 9 refuses outright, and it moved 30 locale folders into
`composeResources`, where they would have stopped matching the app's own language setting. It is kept
only as a record of what was planned; what actually happened is wave 14, and the current state is:

- `:core:keys` **is** a multiplatform module - `com.android.kotlin.multiplatform.library`, targets
  android + jvm + iosArm64 + iosSimulatorArm64, all 47 sources in `commonMain`.
- Its strings **did not move**. They are still `src/androidMain/res/values*/strings.xml`, still
  processed by AAPT, still the same paths in `crowdin.yml`.
- No `R.string` id appears in its public API any more.

### Follow-ups, in rough priority order

1. **Merge `kmp` into `dev`** - no longer a fast-forward, and it gets less free every day.
   (Open decision 11.)
2. **The translation work in section 9a** - 19 of 30 locales are still empty shells.
3. **`PluginDescription.description: Int`** - the `-1` sentinel, set by 57 files. It is now a smaller
   job than it was: `TextRef.Named` exists, and the pattern for generating names is in `buildSrc`.
4. **Remaining `!= 0` / `!= -1` resource sentinels** - `SearchableItem` (2), `MainDrawer`,
   `ManageBottomSheet`, `TreatmentBottomSheet`, `PreferenceScreenView`, `QuickLaunchResolver`,
   `InfoStep`, `SWEventListener`. Harmless today.
5. **`IntPreferenceKey.entries` in the pump modules** still use `entriesResIds`; fine while pumps stay
   Android.
6. ~~Add Apple targets to `:core:data` / `:core:nssdk`.~~ **Done - wave 15.**
7. ~~A `macos-latest` CI job.~~ **Done - wave 16.** `.github/workflows/ios-ci.yml`.
8. **A resolver for `TextRef.Named` off Android.** `KeysStrings` is in `commonMain`, but the id map
   that turns a name into text is `androidMain`. Nothing needs this until there is a non-Android UI,
   and the client only needs the system language, so it is a small generated table when it comes.

### Environment notes for another machine

- **`:wear:kspFullDebugKotlin` fails on the first run of almost every build** with
  `this and base files have different roots: C:\...\dagger-2.60.1.jar!... and E:\Github\AndroidAPS3\wear`.
  It is **not flaky** - it is KSP relativising a path from the Gradle cache on `C:` against a project
  on `E:`. A re-run succeeds because the output is cached. Fix properly by putting `GRADLE_USER_HOME`
  on the same drive as the project.
- **`:pump:combov2:comboctl` and `:pump:danars-emulator`** have async tests that time out under a
  fully parallel build but pass when run alone. Verified twice each. Do not chase them.
- Use redirect, not pipe, for gradle output - a pipe reports the *pipe's* exit code, so a failed
  build looks like it passed.

---

## 10. Side finding - the decimal separator

While reviewing the `.replace(",", ".")` calls, the history turned out to be relevant.

**On a comma locale the numeric keyboard gives a comma, and `toDouble()` accepts only a dot.**
The project has been bitten by this before:

- `52830f620f` "Allow comma in NumberPicker" (2019) - AAPS forked the platform `DigitsKeyListener`
  into a 199 line `DigitsKeyListenerWithComma`, because the stock one swallowed the comma key and
  `"1,5"` became `"15"`.
- `5b36b312ce` "Correction bug Steampunk" (2019) - a watch face parsed a delta string the phone had
  formatted with the device locale, and threw on comma locales. This is exactly the
  format then reparse round trip.
- `f9fa82f1ef` "replace swedish minus sign" (2018) - the Swedish keyboard emits U+2212, so the parse
  silently returned 0 instead of a negative number.

**Today the app is well defended.** Only 4 places use `KeyboardType.Decimal`, and three of them
strip
the comma: `NumberInputRow` (4 read points), `PlusMinusEdit`, `ValueInputDialog`. `SafeParse` does
it
too. There is no `android:inputType` left anywhere - all text input is Compose now.

### One live defect

`FoodManagementScreen.kt:357` uses `KeyboardType.Decimal` with a raw `OutlinedTextField`, and
`FoodManagementViewModel.kt:173` parses it with:

```kotlin
portion = state.editorPortion.toDoubleOrNull() ?: 0.0
```

A user on a comma locale types `12,5`, and the food is saved with `portion = 0.0`. No error, no red
field, Save stays enabled. On an edit this destroys the old value, and the `0.0` syncs to
Nightscout.

**Severity is low.** `portion` is display only - nothing multiplies by it, and the wizard receives
`food.carbs` (an `Int`, `KeyboardType.Number`). So this is lost metadata, not a wrong dose. The fix
is to use the shared `NumberInputRow` instead of a raw text field.

### The tests cannot see locale bugs

`TestBase.kt:28` calls `Locale.setDefault(Locale.ENGLISH)` with no restore, so it leaks into the
whole Gradle test worker. There is no `@Config(qualifiers = ...)` anywhere. On top of that, 19 test
assertions normalise the separator themselves:

```kotlin
assertThat(sut.to1Decimal(1.33).replace(",", ".")).isEqualTo("1.3")  // passes for "1,3" too
```

Those tests cannot fail on a locale bug. Worth noting that `7a5d8e2ec9` "use US locale in tests"
(2018) fixed this properly for `ProfileSealedTest` by pinning the locale, and the 2020 Kotlin
rewrite `89d1de9710` lost the pin and put the `.replace()` back.

This is why the new `NumberFormatTest` sets `ENGLISH`, `GERMAN` and `cs-CZ` explicitly, compares raw
output against real `DecimalFormat` for 17 patterns and 43 values, and restores the locale
afterwards.

### Not merged, maybe worth taking

`b7b1571f72` "OTP: format token with `Locale.ROOT` so it stays ASCII digits" exists only on
`origin/fix/code-quality-audit`. On `dev`, `OneTimePassword.kt:75` still uses `Locale.getDefault()`,
so an Arabic locale device would render OTP tokens with Arabic-Indic digits.
