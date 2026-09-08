package app.aaps.core.interfaces.profile

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock

/**
 * One entry in the local profile list.
 *
 * Pairs a [name] with profile data ([ic], [isf], [basal], [target]) and the glucose unit flag
 * ([mgdl]). The schedules are plain Kotlin data: JSON exists only at the edges — the stored
 * preference document and the Nightscout wire format — never in memory.
 *
 * Immutable and structurally comparable. Edit with [copy]; there is nothing to clone defensively,
 * so a profile handed out by [ProfileRepository] can be held and compared freely.
 *
 * Low and high targets live in ONE [TargetBlock] list rather than two parallel ones. The two JSON
 * arrays must always have matching times and lengths, and pairing them here makes that a property of
 * the type instead of something each reader has to re-check.
 */
data class SingleProfile(
    val name: String,
    val mgdl: Boolean,
    val ic: List<Block>,
    val isf: List<Block>,
    val basal: List<Block>,
    val target: List<TargetBlock>
)
