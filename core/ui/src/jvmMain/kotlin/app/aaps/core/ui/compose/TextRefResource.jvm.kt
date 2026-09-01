package app.aaps.core.ui.compose

import androidx.compose.runtime.Composable
import app.aaps.core.keys.interfaces.TextRef

/**
 * PLACEHOLDER, and unlike the Apple one this is a *waiting* placeholder rather than a blocked one:
 * desktop can resolve these for real, and the work is understood.
 *
 * `GenerateKeyStringsTask` already parses each module's `res/values/strings.xml`. Emitting a third
 * output beside `XxxStrings` and `XxxStringIds` - a `name -> text` map for the JVM - plus a registry
 * mirroring `TextRefIdRegistry` is all the machinery needed, and [TextRef.Named] already carries both
 * the name and the owning module that such a lookup wants.
 *
 * It is not built yet because nothing would call `register()`. On Android that happens from
 * `ResourceHelperImpl`; on desktop the equivalent belongs to the desktop app module, which does not
 * exist. Building the generator and the registry before their caller would ship wiring that nothing
 * activates, so this waits for that module rather than for a decision.
 *
 * [TextRef.AndroidRes] carries a number that means nothing here, so files still using it have to move
 * to [TextRef.Named] first either way.
 */
@Composable
actual fun stringResource(ref: TextRef): String = when (ref) {
    is TextRef.Literal    -> ref.text
    is TextRef.Named      -> ref.name
    is TextRef.AndroidRes -> "?"
}
