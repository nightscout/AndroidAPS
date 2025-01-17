package app.aaps.plugins.aps.autotune.data

import app.aaps.core.data.iob.Iob
import app.aaps.core.data.model.BS
import kotlin.math.exp
import kotlin.math.pow

class LocalInsulin(val name: String?, val peak: Int = DEFAULT_PEAK, private val userDefinedDia: Double = DEFAULT_DIA) {

    val dia
        get(): Double {
            val dia = userDefinedDia
            return if (dia >= MIN_DIA) {
                dia
            } else {
                MIN_DIA
            }
        }

    val duration
        get() = (60 * 60 * 1000L * dia).toLong()

    fun iobCalcForTreatment(bolus: BS, time: Long): Iob {
        val result = Iob()
        if (bolus.amount != 0.0) {
            val bolusTime = bolus.timestamp
            val t = (time - bolusTime) / 1000.0 / 60.0
            val td = dia * 60 //getDIA() always >= MIN_DIA
            val tp = peak.toDouble()
            // force the IOB to 0 if over DIA hours have passed
            if (t < td) {
                val tau = tp * (1 - tp / td) / (1 - 2 * tp / td)
                val a = 2 * tau / td
                val s = 1 / (1 - a + (1 + a) * exp(-td / tau))
                result.activityContrib = bolus.amount * (s / tau.pow(2.0)) * t * (1 - t / td) * exp(-t / tau)
                result.iobContrib = bolus.amount * (1 - s * (1 - a) * ((t.pow(2.0) / (tau * td * (1 - a)) - t / tau - 1) * exp(-t / tau) + 1))
            }
        }
        return result
    }

    companion object {

        private const val MIN_DIA = 5.0
        private const val DEFAULT_DIA = 6.0
        private const val DEFAULT_PEAK = 75
    }
}