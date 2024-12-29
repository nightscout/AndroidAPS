package app.aaps.pump.common.hw.rileylink.ble.data

class FrequencyScanResults {

    @JvmField var trials: MutableList<FrequencyTrial> = ArrayList()
    @JvmField var bestFrequencyMHz = 0.0
    @JvmField var dateTime: Long = 0
    fun sort() {
        trials.sortWith { trial1: FrequencyTrial, trial2: FrequencyTrial ->
            val res = trial1.averageRSSI.compareTo(trial2.averageRSSI)
            if (res == 0) (trial1.frequencyMHz - trial2.frequencyMHz).toInt()
            else res
        }
    }
}
