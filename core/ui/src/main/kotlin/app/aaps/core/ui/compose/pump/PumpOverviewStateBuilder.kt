package app.aaps.core.ui.compose.pump

import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.ui.R

/**
 * Builds the common [PumpInfoRow] items that every pump shares.
 *
 * This is a pure row formatter — each pump ViewModel resolves its own
 * values (from DanaPump, MedtrumPump, Pump interface, etc.) and passes
 * pre-formatted strings here. The builder only attaches labels and
 * visibility logic.
 */
class PumpOverviewStateBuilder(
    private val rh: ResourceHelper
) {

    /**
     * Builds the standard pump overview rows from pre-formatted values.
     *
     * @param lastConnection    Formatted last connection string (e.g. "5 min ago").
     * @param lastBolus         Formatted last bolus string, null to hide the row.
     * @param baseBasalRate     Formatted base basal rate string, null to hide.
     * @param tempBasalText     Formatted temp basal string, empty to hide.
     * @param extendedBolusText Formatted extended bolus string, empty to hide.
     * @param battery           Formatted battery string (e.g. "75%", "3.8V"), null to hide.
     * @param reservoir         Formatted reservoir string (e.g. "120.00 U"), null to hide.
     * @param serialNumber      Serial number string, null or empty to hide.
     */
    fun buildCommonRows(
        lastConnection: String = "",
        lastBolus: String? = null,
        baseBasalRate: String? = null,
        tempBasalText: String = "",
        extendedBolusText: String = "",
        battery: String? = null,
        reservoir: String? = null,
        serialNumber: String? = null
    ): List<PumpInfoRow> = buildList {
        // Last connection
        if (lastConnection.isNotEmpty()) {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.last_connection_label),
                    value = lastConnection
                )
            )
        }

        // Last bolus
        lastBolus?.let {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.last_bolus_label),
                    value = it
                )
            )
        }

        // Base basal rate
        baseBasalRate?.let {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.base_basal_rate_label),
                    value = it
                )
            )
        }

        // Temp basal
        add(
            PumpInfoRow(
                label = rh.gs(R.string.tempbasal_label),
                value = tempBasalText,
                visible = tempBasalText.isNotEmpty()
            )
        )

        // Extended bolus
        add(
            PumpInfoRow(
                label = rh.gs(R.string.extended_bolus_label),
                value = extendedBolusText,
                visible = extendedBolusText.isNotEmpty()
            )
        )

        // Battery
        battery?.let {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.battery_label),
                    value = it
                )
            )
        }

        // Reservoir
        reservoir?.let {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.reservoir_label),
                    value = it
                )
            )
        }

        // Serial number
        serialNumber?.takeIf { it.isNotEmpty() }?.let {
            add(
                PumpInfoRow(
                    label = rh.gs(R.string.serial_number),
                    value = it
                )
            )
        }
    }
}
