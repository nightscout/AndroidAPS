package app.aaps.core.interfaces.pump

import androidx.annotation.StringRes
import app.aaps.core.keys.interfaces.TextRef

/**
 * Sets the comment from an Android string resource.
 *
 * This used to be a member of [PumpEnactResult], which put a resource id in a commonMain interface and
 * so kept the whole result type - and its implementation - on Android. As an extension it keeps the
 * exact call shape (`result.comment(R.string.x)`), needs no resolver of its own because
 * [TextRef.AndroidRes] only carries the id, and leaves the interface platform neutral.
 *
 * A caller outside this package needs `import app.aaps.core.interfaces.pump.comment`.
 */
fun PumpEnactResult.comment(@StringRes id: Int): PumpEnactResult = comment(TextRef.AndroidRes(id))
