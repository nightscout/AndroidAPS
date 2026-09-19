package app.aaps.core.interfaces.pump

import androidx.annotation.StringRes
import app.aaps.core.keys.interfaces.TextRef

/**
 * Sets the comment from an Android string resource.
 *
 * An extension rather than a member, so [PumpEnactResult] itself names no resource id and stays
 * platform neutral. It needs no resolver of its own because [TextRef.AndroidRes] only carries the id.
 *
 * A caller outside this package needs `import app.aaps.core.interfaces.pump.comment`.
 */
fun PumpEnactResult.comment(@StringRes id: Int): PumpEnactResult = comment(TextRef.AndroidRes(id))
