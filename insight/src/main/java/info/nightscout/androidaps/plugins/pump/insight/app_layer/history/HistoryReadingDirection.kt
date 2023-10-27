package info.nightscout.androidaps.plugins.pump.insight.app_layer.history

import info.nightscout.androidaps.plugins.pump.insight.descriptors.BatteryType

enum class HistoryReadingDirection (val id: Int) {
    FORWARD (31),
    BACKWARD (227);
}