package app.aaps.core.interfaces.sync

/**
 * Send data to xDrip+ via Inter-app settings
 */
interface XDripBroadcast {

    /**
     *  Send calibration to xDrip+
     *  Accepting must be enabled in Inter-app settings - Accept Calibrations
     */
    fun sendCalibration(bg: Double): Boolean

    /**
     *  Send data to xDrip+
     *
     *  Accepting must be enabled in Inter-app settings - Accept Glucose/Treatments
     */
    fun sendToXdrip(collection: String, dataPair: DataSyncSelector.DataPair, progress: String)

    /**
     *  Send data to xDrip+
     *
     *  Accepting must be enabled in Inter-app settings - Accept Glucose/Treatments
     */
    fun sendToXdrip(
        collection: String, dataPairs: List<DataSyncSelector.DataPair>, progress:
        String
    )
}