# Eversense integration scaffold

Android Bluetooth classes ported from [PR #191](https://github.com/BernhardRo/AndroidAPS/pull/191), head `c0e4bdfceda05ffeaddc1c12b41f3ce7b7319e35`.

The AAPS 4 adapter lives in `plugins/source/src/androidMain`: Metro registers `EversensePlugin` at key 415, Compose replaces the preference fragments, and typed string preferences preserve the original keys and mmol/L defaults (7.8, 4.4, 11.1, 3.9).

This retains the original scope: scanning is commented out, calibration and connection actions only show messages, and alert history has no implementation. There is no sensor discovery, authentication, glucose ingestion or transmitter configuration yet. Stored glucose targets and alerts do not configure a transmitter or change AAPS treatment settings.
