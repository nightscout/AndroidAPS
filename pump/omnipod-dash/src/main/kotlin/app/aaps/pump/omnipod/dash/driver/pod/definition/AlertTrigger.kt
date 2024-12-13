package app.aaps.pump.omnipod.dash.driver.pod.definition

sealed class AlertTrigger {
    class TimerTrigger(val offsetInMinutes: Short) : AlertTrigger()
    class ReservoirVolumeTrigger(val thresholdInMicroLiters: Short) : AlertTrigger()
}
