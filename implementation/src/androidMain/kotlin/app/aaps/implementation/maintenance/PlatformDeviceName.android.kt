package app.aaps.implementation.maintenance

import android.os.Build

/**
 * Not the answer Android actually exports.
 *
 * `ImportExportPrefsImpl` uses `detectUserName(context)`, which prefers the nickname the user set for
 * the device and needs a `Context` this cannot reach. An actual is required because the expect is in
 * `commonMain`, and the model is the least wrong thing to say without one.
 */
actual fun platformDeviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}"
