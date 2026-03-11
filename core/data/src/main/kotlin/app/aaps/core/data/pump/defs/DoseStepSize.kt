package app.aaps.core.data.pump.defs

import java.util.Locale

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
    ),
    Apex(
        arrayOf(
            DoseStepSizeEntry(0.0, 1.0, 0.025),
            DoseStepSizeEntry(1.0, 2.0, 0.05),
            DoseStepSizeEntry(2.0, Double.MAX_VALUE, 0.1)
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

                sb.append(String.format(Locale.ENGLISH, "%.3f", entry.value))
                    .append(" {")
                    .append(String.format(Locale.ENGLISH, "%.3f", entry.from))
                    .append("-")
                if (entry.to == Double.MAX_VALUE) sb.append("~}")
                else sb.append(String.format(Locale.ENGLISH, "%.3f", entry.to)).append("}")
            }
        }.toString()

    // to = this value is not included, but would actually mean <, so for rates between 0.025-0.975 u/h, we would have [from=0, to=10]
    internal class DoseStepSizeEntry(var from: Double, var to: Double, var value: Double)

}