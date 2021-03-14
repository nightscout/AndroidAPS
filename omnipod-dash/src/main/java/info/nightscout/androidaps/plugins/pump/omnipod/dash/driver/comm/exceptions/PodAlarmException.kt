package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse

class PodAlarmException(val response: AlarmStatusResponse) : Exception("Pod is in alarm: ${response.alarmType.value} ${response.alarmType.name}")