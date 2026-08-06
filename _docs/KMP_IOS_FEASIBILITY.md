# Kotlin Multiplatform and a possible iOS client

Written 2026-08-05. Notes from a review of how far AAPS is from sharing code with an iOS app.

The goal that started this: **an iOS AAPSClient (follower), not a master app.** Not necessarily a
full KMP app, just a way to avoid writing everything again from zero.

All numbers below were measured on the `dev` branch. They are counts of files under `src/`,
build folders excluded.

---

## 1. Short answer

There is **no KMP setup in the project today**. No module uses the multiplatform plugin.
(`:pump:combov2:comboctl` has `commonMain` / `androidMain` folder names left over from the upstream
project, but it builds as a normal Android library.)

The shape of the code is much better than in a typical Android app, mostly because of the Compose
migration and the RxJava removal. The realistic path is **Kotlin Multiplatform with Compose
Multiplatform for the UI**, done step by step, starting with a small working slice on a real iPhone.

---

## 2. What is already fine

| Area                           | State                                                                              | Why it matters                     |
|--------------------------------|------------------------------------------------------------------------------------|------------------------------------|
| `:core:data`                   | 58 files, plain `java-library`, **0** Android imports. Two `expect`/`actual` away, see section 8 | The first module to make multiplatform |
| `:core:keys`                   | 46 files, **0** Android and **0** `java.*` imports since Wave 3                    | Only the 381 `R.string` ids block it |
| Room                           | 46 DAOs, **0** RxJava return types, 22 `suspend`, already on `BundledSQLiteDriver` | This is exactly the Room KMP setup |
| Compose                        | **0** XML layouts in `:core:ui`, 2 left in `:ui`                                   | Compose Multiplatform can use this |
| `LocalContext.current`         | 7 in `:core:ui`, 8 in `:ui`                                                        | Very small coupling to Android     |
| Network code                   | 5 files touch Retrofit / OkHttp, 1 touches socket.io                                | REST part is small enough for Ktor |
| kotlinx.serialization          | 64 files (Gson: 34)                                                                | Already the main choice            |
| kotlinx-datetime               | Declared in `libs.versions.toml`                                                   | Ready to use                       |
| `androidx.lifecycle` ViewModel | Multiplatform since 2.8                                                            | Most of the 129 uses are fine      |
| Vico charts                    | Ships a `multiplatform` artifact                                                   | Only the artifact name changes     |

The biggest surprise was `:core:ui`. Of its 432 files:

- 424 are under `compose/`
- **0** import Dagger or `javax.inject`
- 27 import `app.aaps.core.interfaces`
- 34 import `app.aaps.core.keys`
- **361 (84%) import none of Android, Dagger or the AAPS interfaces**

So most of the Compose work of the last year can be reused.

---

## 3. What does not work on iOS

### Libraries with no Kotlin/Native version

| Library                                                | Files                                                  | Replacement                            |
|--------------------------------------------------------|--------------------------------------------------------|----------------------------------------|
| Dagger / Hilt                                          | ~300                                                   | kotlin-inject, Metro, or Koin          |
| RxJava 3                                               | RxBus in 35 `:ui`, 19 config, 18 sync, 13 impl, 12 aps | SharedFlow                             |
| Retrofit + OkHttp                                      | 6                                                      | Ktor client                            |
| socket.io-client                                       | 1 (`NSClientV3Service`)                                | **Do not replace** - see section 3a    |
| Gson                                                   | 34                                                     | kotlinx.serialization                  |
| `org.json` (`JSONObject`)                              | 243                                                    | kotlinx `JsonObject` - see section 8   |
| WorkManager                                            | 44                                                     | See warning below                      |
| joda-time                                              | 5                                                      | kotlinx-datetime                       |
| `java.text.DecimalFormat`                              | 74                                                     | Done, see section 8                    |
| `java.text.SimpleDateFormat`                           | 7                                                      | kotlinx-datetime formatting            |
| `java.util.concurrent.TimeUnit`                        | 108 files, but only 93 sites convertible               | `kotlin.time.Duration`                 |
| `Executors`, `ConcurrentHashMap`                       | 14                                                     | Coroutine dispatchers, map plus mutex  |
| `java.security`, `javax.crypto`                        | ~20                                                    | cryptography-kotlin, or expect/actual  |
| `java.io.File`                                         | 12                                                     | okio                                   |
| spongycastle, tink-android                             | 2                                                      | as above                               |
| commons-lang3, Guava                                   | 4                                                      | Inline the few helpers                 |
| slf4j, logback-android                                 | 4                                                      | Kermit or Napier                       |
| kotlin-reflect                                         | 9                                                      | No reflection on Native, must go       |
| Firebase                                               | 4                                                      | GitLive Firebase KMP, or expect/actual |
| Play Services                                          | 2                                                      | Android only by nature                 |
| androidx.glance (widgets)                              | 7                                                      | WidgetKit is Swift only, no reuse      |
| Garmin, osmdroid, androidsvg, appauth, java-otp, zxing | 1-3 each                                               | Mostly not client features             |

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

| Where | What |
| --- | --- |
| Android | keep `io.socket:socket.io-client:2.1.2` **unchanged** |
| iOS | `socket.io-client-swift` |
| shared | a small `expect interface` over the calls actually used |

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

| Link | Why |
| --- | --- |
| [moko-socket-io] | Cleanest reference. Small, `expect class Socket` over the two native clients. |
| [KotSock] | Same approach, a second opinion on the API shape. |
| [moko-socket-io-sample] | A working KMP app using it, by the moko maintainer. Shows the CocoaPods wiring for `socket.io-client-swift`. |
| [KMP Socket.IO deep dive] | Walks through building exactly this kind of wrapper. Closest thing to the POC we would be reproducing. |

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

| Part | Version | Protocol |
| --- | --- | --- |
| Nightscout `cgm-remote-monitor` 15.0.7 | `socket.io ~4.5.4` | **v4** |
| AAPS today, Android | `io.socket:socket.io-client:2.1.2` | v3 / v4 - correct |
| iOS should use | `Socket.IO-Client-Swift` **16.x** | v3 / v4 |
| moko-socket-io 0.6.0, iOS half | `Socket.IO-Client-Swift ~> 15.2.0` | **v2 - will not talk to Nightscout** |

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
   today with `app.aaps.core.ui.R` and `app.aaps.ui.R`.

### What this means for `:core:keys`

The 368 strings themselves move without trouble. The real work is that the key classes carry
`@StringRes Int`, and a Compose Multiplatform resource is a `StringResource`, not an `Int`. That
type change is the job, not the translations.

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

**Where this stands.** Step 1 is done. Half of step 0 is done too, and out of order: `:core:data`
already builds for Kotlin/Native (wave 5), and part of step 5 has been pulled forward because
`:core:nssdk` turned out to be sliceable after all (wave 6). What is still missing from step 0 is the
half that needs a Mac - a real device, and an honest look at Compose Multiplatform on iOS. No amount
of further blocker removal answers that question, which is the argument for doing it soon rather than
continuing down the list.

---

## 8. Work done so far

Committed on `dev`:

| Commit | What |
| --- | --- |
| `e5f4e27626` | Migrate DecimalFormat |
| `a42d823c93` | Eliminate TimeUnit |
| `e1068e77db` | `:core:keys` remove JVM dependency |
| `35b5399798` | Extract dependencies |
| `1aed547f7a` | cleanup (`TB.isInProgress` moved out of `:core:data`) |

Committed on `kmp/core-data-experiment`, a throwaway branch kept because the work turned out to be
worth keeping (see waves 5 and 6):

| Commit | What |
| --- | --- |
| `67ecb1e696` | Going KMP - `:core:data` is a real multiplatform module |
| `e24476237b` | Prepare `:core:nssdk` - dead code out, defaults in, characterization tests |
| `f6a4e85e8a` | `:core:nssdk` `org.json` -> kotlinx |

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
  when the attempt was stopped, with test sources not yet compiled. Gradle stops at the first failing
  module and Kotlin caps reported errors per file - in `PersistenceLayerImpl` it named 10 of 31.
- **`utcOffset` is not a throwaway field.** It is stored in every table, it is part of
  `contentEqualsTo` (so a changed value makes a record compare unequal and re-sync), it is uploaded
  to Nightscout - where the server validates it and `NSAndroidClientImpl` has to catch
  `"Bad or missing utcOffset field"` 400s and retry with 0 - it goes to Open Humans, and it is shown
  in the user entry history. Recomputing it by hand at 50+ sites risks silently changing stored
  values; `expect` / `actual` keeps the computation identical at every site and changes no call site.

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

| Was | Now |
| --- | --- |
| `System.currentTimeMillis()` | gone (`T.now()` deleted, `isInProgress` moved out) |
| `java.util.Locale` | gone (`DoseStepSize`) |
| `java.util.concurrent.TimeUnit` | gone (Wave 2) |
| `java.util.TimeZone`, 17 files | `expect` / `actual`, no call site changes |
| `NumberFormatPlatform` | `expect` / `actual`, by design |

Two `expect` / `actual` declarations away from compiling as `commonMain`.

### Wave 5 - `:core:data` really is multiplatform (done)

`:core:data` now uses `kotlin("multiplatform")` with a `jvm()` target and `mingwX64()` as a stand-in
for iOS (real iOS targets need macOS and Xcode, so Windows cannot build them - `mingwX64` proves the
code compiles for Kotlin/Native, which is the part that was in doubt).

**Zero consumer changes.** All 13 modules that depend on `:core:data` build unmodified: an Android
consumer still sees a normal library, because a multiplatform module publishes a normal variant.
That was the single most valuable thing to learn, and the reason it was worth doing on a branch.

Two `expect` / `actual` seams, both deliberate:

| Seam | Why |
| --- | --- |
| `NumberFormatPlatform` | number formatting is genuinely platform work |
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
and `RunningConfiguration` (2), carrying profiles and settings - the two document kinds AAPS does not
model, because it does not own their shape. It is now gone from the module.

Why it could be done alone: both directions already round-tripped through text, so `org.json` was
only ever a carrier.

```kotlin
JSONObject(json.asJsonObject.toString())                      // read  - Gson tree -> text -> org.json
api.createSetting(JsonParser.parseString(doc.toString()))     // write - org.json -> text -> Gson tree
```

Swapping the carrier for kotlinx `JsonObject` left the write line **character for character
identical** and changed one parse call on the read side. Gson, Retrofit and the 210 `@SerializedName`
annotations were not touched.

**The trap, and why tests came first.** The two libraries disagree about a missing key, and nothing
about the difference shows up at compile time:

| accessor | `org.json`, missing key | `org.json`, explicit `null` | kotlinx, missing key |
| --- | --- | --- | --- |
| `optString` | `""` | `"null"` - the four letter text | `null` |
| `optJSONObject` | `null` | `null` | `null` |
| `optLong(k, 0)` | `0` | `0` | `null` |
| `optBoolean` | `false` | `false` | `null` |

A straight translation would silently flip every downstream `isEmpty()` and `?:`. So the swap was
done through `OrgJsonCompat`, kotlinx accessors that reproduce `org.json` exactly, golden-mastered
against the real thing over 21 inputs x 5 accessors. The type change is then equivalent by
construction rather than by inspection.

The one genuine divergence: reading an **object** through `optString` differs in key order, because
`org.json` iterates a hash map and its order is unspecified. No call site does it - all six keys read
through `optString` hold strings - so it is documented and skipped rather than pinned.

**What stays on `org.json`, on purpose.** `JsonBridge` marks the two boundaries:

- **socket.io** hands every payload over as `org.json.JSONObject`. That is the library's API, so the
  conversion happens as the event arrives and everything downstream is kotlinx.
- **The profile subsystem** - `ProfileStore`, `PureProfile`, `DataSyncSelector.PairProfileStore` - is
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
`RunningConfigurationPublisher` mutated a *nested* object to mask `NsClientAllowClientControl` inside
`syncedPrefs`, and that is now an explicit rebuild preserving key order and the masking semantics.

The wire format's mixed typing was preserved deliberately: `isFakingTempsByExtendedBoluses` is a real
JSON boolean while every `syncedPrefs` value is a string, exactly as `org.json` wrote them.

**Left unfixed, on purpose.** `optStringCompat` faithfully reproduces the `"null"` quirk, so a server
sending `{"message":null}` still writes the word "null" into the user visible NSClient log. Changing
behaviour during a type migration is how regressions get smuggled in; that is a separate change.

**What is left in `:core:nssdk`:**

| Dependency | Files | Slice |
| --- | --- | --- |
| Gson | 17 | the converter switch |
| joda-time | 1 | with the converter - `RemoteTreatment` lenient dates |
| Retrofit | 4 | Ktor |
| OkHttp | 3 | Ktor |
| `java.io.IOException` | 1 | with Ktor - the exception hierarchy |
| `android.*` | 2 | with Ktor - mainly `Context` for the OkHttp disk cache |

Verified on WSA with a full sync. That specifically exercises the riskiest part: settings and profile
reads both go through the changed Gson type adapter, and kotlinx `JsonObject` also implements
`Map<String, JsonElement>`, so Gson picking its built-in map adapter instead of the registered one
was a real possibility that would have failed quietly rather than thrown.

### Was behaviour preserved?

An audit ran five parallel agents against the migrated code, each trying to find an input where old
and new differ, with a second pass trying to refute what they found. Results worth keeping:

- **`DecimalFormat` -> `NumberFormat`: identical.** 17 patterns against ~90 values across 57 locales,
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

| Change | Effect |
| --- | --- |
| `qs()` no longer groups | `1234.5` with 1 digit: en `1,234.5` -> `1234.5`, de `1.234.5` -> `1234.5` |
| `formatUS` uses `Locale.US` symbols | correct in Arabic; also swaps U+2212 for `-` on sv, nb, lt, hr, fi, sl, et |
| automation `%d` patterns | the literal-digit bug is gone, but nothing renders it |

`qs()` also gained a `max(0, digits)` guard: `DecimalFormat` used to clamp a negative
`maximumFractionDigits` to 0, while `NumberFormat` throws. No caller passes a negative, but the guard
keeps the old contract on a public interface method.

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
4. ~~Use `DateUtil` instead of `System.currentTimeMillis()` everywhere?~~ **Decided: only where it is
   needed.** The principle is right - `DateUtil` is an interface, so it mocks in tests and can differ
   per platform - but the sweep would be **547 sites in 227 files**, plus 62 `TimeZone.getDefault()`
   in 57 files, and most of it sits in pump drivers that will never be KMP. Do it in the modules on
   the KMP path, leave `:pump:*` and `:wear` alone, and make it a rule for new code so the number
   stops growing.
5. ~~Which branch for the first KMP module?~~ **Decided: `kmp/core-data-experiment`, and it worked.**
   The fear was that converting `:core:data` to `kotlin("multiplatform")` would change how Gradle
   resolves it for the 13 dependent modules. It does not - a multiplatform module still publishes a
   normal Android variant, and all 13 built unmodified. The branch was meant to be thrown away and is
   now worth merging.
6. **Still open: `wear/SmallestDoubleString.kt`** - the last `DecimalFormat` in a non-test file that
   is not the deliberate platform seam. It builds patterns from a runtime string, so it needs real
   logic rather than a mapping.
7. **Still open: the `:core:nssdk` converter switch.** Gson to kotlinx is atomic - 210
   `@SerializedName`, the joda date parsing and the `IOException` hierarchy move together. Two things
   need deciding first: the `Json { }` configuration (`ignoreUnknownKeys = true`, `explicitNulls =
   false`), and what to do about the ~8 **non-null** fields that were deliberately left without
   defaults. Gson currently leaves those null through `Unsafe.allocateInstance`; kotlinx would throw.
   For `RemoteStatusResponse` throwing is arguably correct, since `v3/status` always sends them.
   `RemoteFood` from a foreign uploader is the real question.
8. **Still open: Retrofit converter, or straight to Ktor?** Adding
   `retrofit2-kotlinx-serialization-converter` is the smaller step but adds a dependency that gets
   thrown away when Ktor lands. Going straight to Ktor avoids that and is the actual destination, and
   its failure mode ("no network") is louder than a serialization-only swap ("quietly wrong data").
   Leaning Ktor.
9. **Still open: the OkHttp disk cache needs a `Context`.** One of the two remaining `android.*`
   imports in `:core:nssdk`. Ktor on iOS needs either a different cache story or none.
10. **Still open: R8 has never run against the KMP module.** Both device checks were debug builds, so
    minification against multiplatform metadata and the `expect` / `actual` pairs is untested. This is
    the only real remaining risk on the branch.

Waves 1 to 4 are committed on `dev`. Waves 5 and 6 are committed on `kmp/core-data-experiment`, both
verified on WSA against a live Nightscout. The `FoodManagement` comma defect in section 10 is found
but not fixed.

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
