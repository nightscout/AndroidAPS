package app.aaps.core.interfaces.queue

import androidx.annotation.StringRes
import app.aaps.core.keys.interfaces.TextRef

/**
 * Cancels the command with a reason given as an Android string resource.
 *
 * Same reason as `PumpEnactResult.comment(Int)`: the member takes a [TextRef] so the interface stays
 * platform neutral, and this keeps the resource id call shape for Android callers.
 *
 * A caller outside this package needs `import app.aaps.core.interfaces.queue.cancel`.
 */
fun Command.cancel(@StringRes commentResId: Int, success: Boolean = true): Unit =
    cancel(TextRef.AndroidRes(commentResId), success)
