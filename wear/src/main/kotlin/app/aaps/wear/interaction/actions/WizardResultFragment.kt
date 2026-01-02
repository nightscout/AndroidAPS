package app.aaps.wear.interaction.actions

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import app.aaps.wear.R
import app.aaps.wear.interaction.utils.WizardResultViewBuilder
import app.aaps.wear.interaction.utils.WizardCalculationRow
import dagger.android.support.AndroidSupportInjection
import java.text.DecimalFormat

class WizardResultFragment : Fragment() {

    private val decimalFormat = DecimalFormat("0.00")
    private val oneDecimalFormat = DecimalFormat("0.0")

    companion object {
        fun newInstance(
            totalInsulin: Double,
            carbs: Int,
            ic: Double,
            sens: Double,
            insulinCarbs: Double,
            insulinBg: Double?,
            insulinCob: Double?,
            insulinBolusIob: Double?,
            insulinBasalIob: Double?,
            insulinTrend: Double?,
            tempTarget: String?,
            percentage: Int?,
            totalBeforePercentage: Double?,
            cob: Double
        ): WizardResultFragment {
            return WizardResultFragment().apply {
                arguments = Bundle().apply {
                    putDouble("total_insulin", totalInsulin)
                    putInt("carbs", carbs)
                    putDouble("ic", ic)
                    putDouble("sens", sens)
                    putDouble("insulin_carbs", insulinCarbs)
                    putDouble("insulin_bg", insulinBg ?: Double.NaN)
                    putDouble("insulin_cob", insulinCob ?: Double.NaN)
                    putDouble("insulin_bolus_iob", insulinBolusIob ?: Double.NaN)
                    putDouble("insulin_basal_iob", insulinBasalIob ?: Double.NaN)
                    putDouble("insulin_trend", insulinTrend ?: Double.NaN)
                    putString("temp_target", tempTarget)
                    putInt("percentage", percentage ?: 100)
                    putDouble("total_before_percentage", totalBeforePercentage ?: Double.NaN)
                    putDouble("cob", cob)
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_wizard_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return

        // Get all the data
        val totalInsulin = args.getDouble("total_insulin")
        val carbs = args.getInt("carbs")
        val ic = args.getDouble("ic")
        val sens = args.getDouble("sens")
        val insulinCarbs = args.getDouble("insulin_carbs")
        val insulinBg = args.getDouble("insulin_bg")
        val insulinCob = args.getDouble("insulin_cob")
        val insulinBolusIob = args.getDouble("insulin_bolus_iob")
        val insulinBasalIob = args.getDouble("insulin_basal_iob")
        val insulinTrend = args.getDouble("insulin_trend")
        val tempTarget = args.getString("temp_target")
        val percentage = args.getInt("percentage")
        val totalBeforePercentage = args.getDouble("total_before_percentage")
        val cob = args.getDouble("cob")

        // Set up the UI
        view.findViewById<TextView>(R.id.wizard_total_insulin).text = decimalFormat.format(totalInsulin)
        view.findViewById<TextView>(R.id.wizard_carbs).text = getString(R.string.wizard_carbs_format, carbs)

        if (ic > 0 && sens > 0) {
            view.findViewById<TextView>(R.id.wizard_settings).text =
                getString(R.string.wizard_settings_format, oneDecimalFormat.format(ic), oneDecimalFormat.format(sens))
        }

        // Build calculation rows
        val calculationRowsContainer = view.findViewById<LinearLayout>(R.id.wizard_calculation_rows)
        val builder = WizardResultViewBuilder(requireContext())
        val rows = mutableListOf<WizardCalculationRow>()

        // 1. BG correction if used - with TT on same line
        if (!insulinBg.isNaN()) {
            val bgLabel = if (!tempTarget.isNullOrEmpty()) getString(R.string.wizard_result_bg_tt, tempTarget) else getString(R.string.wizard_result_bg)
            rows.add(WizardCalculationRow(bgLabel, insulinBg))
        }

        // 2. Trend if used
        if (!insulinTrend.isNaN() && insulinTrend != 0.0) {
            rows.add(WizardCalculationRow(getString(R.string.wizard_result_trend), insulinTrend))
        }

        // 3. IOB if used (combine bolus and basal)
        val totalIob = when {
            !insulinBolusIob.isNaN() && !insulinBasalIob.isNaN() -> insulinBolusIob + insulinBasalIob
            !insulinBolusIob.isNaN() -> insulinBolusIob
            !insulinBasalIob.isNaN() -> insulinBasalIob
            else -> Double.NaN
        }
        if (!totalIob.isNaN() && totalIob != 0.0) {
            rows.add(WizardCalculationRow(getString(R.string.wizard_result_iob), totalIob))
        }

        // 4. COB if used
        if (!insulinCob.isNaN() && insulinCob != 0.0) {
            rows.add(WizardCalculationRow(getString(R.string.wizard_result_cob, cob), insulinCob))
        }

        // 5. Carbs
        if (carbs > 0) {
            rows.add(WizardCalculationRow(getString(R.string.wizard_result_carbs, carbs), insulinCarbs))
        }

        // Add all rows to UI
        rows.forEach { row ->
            builder.addCalculationRow(calculationRowsContainer, row)
        }

        // Add percentage breakdown if needed
        if (percentage != 100 && !totalBeforePercentage.isNaN() && totalBeforePercentage > 0) {

            // Only show divider + subtotal if there's more than 1 calculation row
            if (rows.size > 1) {
                val divider = View(requireContext())
                divider.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    2
                ).apply {
                    setMargins(0, 8, 0, 8)
                }
                divider.setBackgroundColor(resources.getColor(R.color.divider, null))
                calculationRowsContainer.addView(divider)

                builder.addCalculationRow(calculationRowsContainer, WizardCalculationRow(getString(R.string.wizard_result_subtotal), totalBeforePercentage))
            }
            // Always show the percentage correction row
            val percentageAdjustment = totalInsulin - totalBeforePercentage
            builder.addCalculationRow(calculationRowsContainer, WizardCalculationRow(getString(R.string.wizard_result_correction_percentage, percentage), percentageAdjustment))
        }

        // Add final total
        val divider2 = View(requireContext())
        divider2.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            2
        ).apply {
            setMargins(0, 8, 0, 8)
        }
        divider2.setBackgroundColor(resources.getColor(R.color.divider, null))
        calculationRowsContainer.addView(divider2)

        builder.addCalculationRow(calculationRowsContainer, WizardCalculationRow(getString(R.string.wizard_result_total), totalInsulin))

        // Setup collapsible calculation details
        setupCalculationToggle(view)
    }

    private fun setupCalculationToggle(view: View) {
        val calculationHeader = view.findViewById<LinearLayout>(R.id.calculation_header)
        val calculationDetails = view.findViewById<LinearLayout>(R.id.calculation_details)
        val expandIcon = view.findViewById<TextView>(R.id.expand_icon)

        var isExpanded = false

        calculationHeader.setOnClickListener {
            isExpanded = !isExpanded

            if (isExpanded) {
                calculationDetails.visibility = View.VISIBLE
                expandIcon.text = "▲"

                // Scroll to bottom after expansion
                view.findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll_view)?.postDelayed({
                                                                                                            view.findViewById<androidx.core.widget.NestedScrollView>(R.id.scroll_view)?.fullScroll(View.FOCUS_DOWN)
                                                                                                        }, 100)
            } else {
                calculationDetails.visibility = View.GONE
                expandIcon.text = "▼"
            }
        }
    }
}