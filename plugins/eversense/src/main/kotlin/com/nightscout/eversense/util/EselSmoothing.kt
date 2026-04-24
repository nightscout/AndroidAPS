package com.nightscout.eversense.util

import kotlin.math.min
import kotlin.math.round

class EselSmoothing {
    companion object {
        private const val factor: Double = 0.3
        private const val correction: Double = 0.5
        private const val descent_factor: Double = 0.0

        fun smooth(currentRaw: Int, lastSmooth: Int, lastRaw: Int): Int {
            val value = currentRaw.toDouble()

            // exponential smoothing, see https://en.wikipedia.org/wiki/Exponential_smoothing
            // y'[t]=y'[t-1] + (a*(y-y'[t-1])) = a*y+(1-a)*y'[t-1]
            // factor is a, value is y, lastSmooth y'[t-1], smooth y'
            // factor between 0 and 1, default 0.3
            // factor = 0: always last smooth (constant)
            // factor = 1: no smoothing
            var smooth: Double = lastSmooth + (factor * (value - lastSmooth))

            // correction: average of delta between raw and smooth value, added to smooth with correction factor
            // correction between 0 and 1, default 0.5
            // correction = 0: no correction, full smoothing
            // correction > 0: less smoothing
            smooth += (correction * ((lastRaw - lastSmooth) + (value - smooth)) / 2.0)
            smooth -= descent_factor * (smooth - min(value, smooth))

            return round(smooth).toInt()
        }
    }
}