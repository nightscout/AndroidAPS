package app.aaps.ui.compose.profileHelper.defaultProfile

import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.data.Block
import app.aaps.core.data.model.data.TargetBlock
import app.aaps.core.data.time.T
import app.aaps.core.data.time.systemUtcOffsetAt
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.profile.PureProfile
import app.aaps.core.interfaces.utils.DateUtil
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultProfileDPV @Inject constructor(
    private val dateUtil: DateUtil,
    private val profileUtil: ProfileUtil
) {

    private var oneToFive = arrayOf(3.97, 3.61, 3.46, 3.70, 3.76, 3.87, 4.18, 4.01, 3.76, 3.54, 3.15, 2.80, 2.86, 3.21, 3.61, 3.97, 4.43, 4.96, 5.10, 5.50, 5.81, 6.14, 5.52, 5.10)
    private var sixToEleven = arrayOf(4.20, 4.27, 4.41, 4.62, 4.92, 5.09, 5.01, 4.47, 3.89, 3.33, 3.10, 2.91, 2.97, 3.08, 3.36, 3.93, 4.52, 4.76, 4.69, 4.63, 4.63, 4.47, 4.47, 4.31)
    private var twelveToEighteen = arrayOf(3.47, 3.80, 4.31, 4.95, 5.59, 6.11, 5.89, 5.11, 4.31, 3.78, 3.55, 3.39, 3.35, 3.39, 3.64, 3.97, 4.53, 4.59, 4.50, 4.00, 3.69, 3.39, 3.35, 3.35)

    /**
     * Builds the profile directly.
     *
     * This used to assemble a JSON document and immediately parse it back with `pureProfileFromJson`.
     * `dia`, `carbs_hr` and `delay` are gone with that document because nothing ever read them back -
     * they are not part of a [PureProfile], and the DIA comes from the insulin configuration.
     */
    fun profile(age: Int, tdd: Double, basalSumPct: Double, units: GlucoseUnit): PureProfile? {
        val basalSum = tdd * basalSumPct
        val basalBlocks = when (age) {
            in 1..5   -> hourlyBlocks(oneToFive, basalSum)
            in 6..11  -> hourlyBlocks(sixToEleven, basalSum)
            in 12..18 -> hourlyBlocks(twelveToEighteen, basalSum)
            // Ages outside 1..18, which includes the old `age > 18` branch.
            else      -> return null
        }
        val target = profileUtil.fromMgdlToUnits(108.0, units)
        return PureProfile(
            basalBlocks = basalBlocks,
            isfBlocks = wholeDay(profileUtil.fromMmolToUnits(0.0, units)),
            icBlocks = wholeDay(0.0),
            targetBlocks = listOf(TargetBlock(T.hours(24).msecs(), target, target)),
            glucoseUnit = units,
            utcOffset = systemUtcOffsetAt(dateUtil.now())
        )
    }

    /** 24 one-hour blocks, each a percentage of the daily basal total. */
    private fun hourlyBlocks(b: Array<Double>, basalSum: Double): List<Block> =
        (0..23).map { Block(T.hours(1).msecs(), b[it] * basalSum / 100.0) }

    /** One block covering the whole day. */
    private fun wholeDay(value: Double): List<Block> =
        listOf(Block(T.hours(24).msecs(), value))
}
