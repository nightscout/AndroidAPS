package info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.comm.exceptions

import info.nightscout.androidaps.plugins.pump.omnipod.dash.driver.pod.response.AlarmStatusResponse
import java.util.*

class PodAlarmException(val response: AlarmStatusResponse) : Exception(
    String.format(
        Locale.getDefault(),
        "Pod is in alarm: %03d %s",
        response.alarmType.value.toInt() and 0xff,
        response.alarmType.name
    )
)
