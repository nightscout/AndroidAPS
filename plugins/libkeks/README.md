# `:plugins:libkeks`

**KEKS** J-PAKE / ExtraData authentication for Dexcom ONE+ / G7, translated to Kotlin from
[NightscoutFoundation/xDrip](https://github.com/NightscoutFoundation/xDrip) at pin
`1e86d9a2a52577ed2c30fbf7b69d75fd56e6918f` (tag 2026.07.15, GPL-3.0).

This is a **translation, not a verbatim copy**. The 24 Java files of the pin were rewritten in
Kotlin Multiplatform code; BouncyCastle, Lombok and `javax.crypto` are gone from the shared code.
See `NOTICE` for exactly what changed.

**How to check it:** `src/commonTest` holds golden values recorded from the original Java and
BouncyCastle code at the pin, including a whole pairing replayed message by message. The Kotlin
code must give the same bytes:

```
./gradlew :plugins:libkeks:jvmTest :plugins:libkeks:testAndroidHostTest
```

Targets: Android and the JVM (desktop). Only three things are left to the platform, each behind an
`expect` in `crypto/KeksPlatform.kt`: a secure random generator, one AES block in ECB mode, and
where a log line goes.

Used by the Dexcom ONE+ / G7 driver (`:plugins:dexcom_oneplus`).
