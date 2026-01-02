package app.aaps.wear.interaction.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import app.aaps.wear.R

data class WizardCalculationRow(
    val label: String,
    val value: Double,
    val unitResId: Int = R.string.insulin_unit_short
)

class WizardResultViewBuilder(private val context: Context) {

    fun addCalculationRow(
        container: LinearLayout,
        row: WizardCalculationRow
    ) {
        val rowView = LayoutInflater.from(context)
            .inflate(R.layout.wizard_calculation_row, container, false)

        val labelView = rowView.findViewById<TextView>(R.id.calc_row_label)
        val valueView = rowView.findViewById<TextView>(R.id.calc_row_value)
        val iconView = rowView.findViewById<View>(R.id.calc_row_indicator)

        labelView.text = row.label

        // Format value with sign
        val formattedValue = if (row.value >= 0) {
            "+${String.format("%.2f", row.value)}"
        } else {
            String.format("%.2f", row.value)
        }

        // Get unit from string resource
        val unit = context.getString(row.unitResId)
        valueView.text = "$formattedValue $unit"

        // Color coding
        val color = when {
            row.value > 0 -> ContextCompat.getColor(context, R.color.insulin_positive)
            row.value < 0 -> ContextCompat.getColor(context, R.color.insulin_negative)
            else -> ContextCompat.getColor(context, R.color.secondary_text)
        }
        valueView.setTextColor(color)

        // Indicator dot
        iconView.setBackgroundResource(
            if (row.value >= 0) R.drawable.indicator_positive
            else R.drawable.indicator_negative
        )

        container.addView(rowView)
    }
}