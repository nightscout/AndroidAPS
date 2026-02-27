package app.aaps.pump.omnipod.common.bledriver.pod.definition

sealed class AlertTrigger {
    class TimerTrigger(val offsetInMinutes: Short) : AlertTrigger()
    class ReservoirVolumeTrigger(val thresholdInMicroLiters: Short) : AlertTrigger()
}
