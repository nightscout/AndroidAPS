package app.aaps.core.data.pump.defs

import app.aaps.core.data.format.NumberFormat
import app.aaps.core.data.format.NumberFormatPlatform

enum class DoseStepSize(private val entries: Array<DoseStepSizeEntry>) {

    ComboBasal(
        arrayOf(
            DoseStepSizeEntry(0.0, 1.0, 0.01),
            DoseStepSizeEntry(1.0, 10.0, 0.05),
            DoseStepSizeEntry(10.0, Double.MAX_VALUE, 0.1)
        )
    ),
    InsightBolus(
        arrayOf(
            DoseStepSizeEntry(0.0, 2.0, 0.05),
            DoseStepSizeEntry(2.0, 5.0, 0.1),
            DoseStepSizeEntry(5.0, 10.0, 0.2),
            DoseStepSizeEntry(10.0, Double.MAX_VALUE, 0.5)
        )
    ),
    InsightBasal(
        arrayOf(
            DoseStepSizeEntry(0.0, 5.0, 0.01),
            DoseStepSizeEntry(5.0, Double.MAX_VALUE, 0.1)
        )
    ),
    MedtronicVeoBasal(
        arrayOf(
            DoseStepSizeEntry(0.0, 1.0, 0.025),
            DoseStepSizeEntry(1.0, 10.0, 0.05),
            DoseStepSizeEntry(10.0, Double.MAX_VALUE, 0.1)
        )
    ),
    YpsopumpBasal(
        arrayOf(
            DoseStepSizeEntry(0.0, 1.0, 0.01),
            DoseStepSizeEntry(1.0, 2.0, 0.02),
            DoseStepSizeEntry(2.0, 15.0, 0.1),
            DoseStepSizeEntry(15.0, 40.0, 0.5)
        )
    );

    fun getStepSizeForAmount(amount: Double): Double {
        for (entry in entries)
            if (entry.from <= amount && entry.to > amount) return entry.value

        // should never come to this
        return entries[entries.size - 1].value
    }

    val description: String
        get() = StringBuilder().also { sb ->
            var first = true
            for (entry in entries) {
                if (first) first = false else sb.append(", ")

                sb.append(entry.value.dotted())
                    .append(" {")
                    .append(entry.from.dotted())
                    .append("-")
                if (entry.to == Double.MAX_VALUE) sb.append("~}")
                else sb.append(entry.to.dotted()).append("}")
            }
        }.toString()

    /** Three decimals with a dot, whatever the device locale is. This text goes into logs. */
    private fun Double.dotted(): String = NumberFormat.DECIMAL_3.format(this, NumberFormatPlatform.SEPARATOR_DOT)

    // to = this value is not included, but would actually mean <, so for rates between 0.025-0.975 u/h, we would have [from=0, to=10]
    internal class DoseStepSizeEntry(var from: Double, var to: Double, var value: Double)

}