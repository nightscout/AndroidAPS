package app.aaps.core.data.ui

/** How big a treatment marker is drawn, from its amount against the others of the same kind. */
enum class MarkerTier { SMALL, MEDIUM, LARGE }

/**
 * The two cuts between the three [MarkerTier] classes. An amount under [smallBelow] is small, an
 * amount from [largeFrom] is large, the rest is medium.
 *
 * The phone takes the cuts from the treatments it has loaded, a whole day, and sends them to the
 * watch with the treatments, so the same SMB, bolus or carbs entry is drawn with the same size on
 * both. Without cuts there is nothing to measure against, and everything is drawn medium.
 */
class MarkerSizeClasses(val smallBelow: Double, val largeFrom: Double) {

    fun of(amount: Double): MarkerTier = when {
        amount < smallBelow -> MarkerTier.SMALL
        amount < largeFrom  -> MarkerTier.MEDIUM
        else                -> MarkerTier.LARGE
    }

    companion object {

        /**
         * Thirds of the range from zero to the largest of [amounts]: the rule of every marker on the
         * phone's graphs. Measured from zero, not from the smallest amount: 0.20 U and 0.25 U are the
         * same dose to a reader, and splitting the span between them would draw one as the smallest
         * marker and the other as the largest. Null without a positive amount.
         */
        fun ofMax(amounts: List<Double>): MarkerSizeClasses? = ofMax(amounts.maxOrNull())

        /** [ofMax] against a largest amount that is already known. Null when [max] is null or not positive. */
        fun ofMax(max: Double?): MarkerSizeClasses? {
            if (max == null || max <= 0.0) return null
            return MarkerSizeClasses(smallBelow = max / 3.0, largeFrom = 2.0 * max / 3.0)
        }
    }
}
