package com.nightscout.eversense.packets

import android.content.SharedPreferences
import com.nightscout.eversense.EversenseGattCallback
import com.nightscout.eversense.callbacks.EversenseWatcher
import com.nightscout.eversense.models.EversenseTransmitterSettings
import kotlinx.serialization.json.Json

class Eversense365Communicator {
    companion object {
        private const val TAG = "EversenseE3Communicator"
        private val JSON = Json { ignoreUnknownKeys = true }

        fun readGlucose(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>) {
            // TODO:
        }

        fun fullSync(gatt: EversenseGattCallback, preferences: SharedPreferences, watchers: List<EversenseWatcher>) {
            // TODO:
        }

        fun writeSettings(gatt: EversenseGattCallback, preferences: SharedPreferences, settings: EversenseTransmitterSettings): Boolean {
            // TODO:
            return false
        }
    }
}