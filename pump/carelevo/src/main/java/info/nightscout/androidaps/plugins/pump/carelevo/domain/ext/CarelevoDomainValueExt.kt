package info.nightscout.androidaps.plugins.pump.carelevo.domain.ext

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.basal.CarelevoBasalSegmentDomainModel
import java.util.UUID

internal fun List<CarelevoBasalSegmentDomainModel>.splitSegment(): List<CarelevoBasalSegmentDomainModel> {
    val normalized = this
        .map { seg ->
            val startHour = (seg.startTime / 60).coerceIn(0, 23)
            val endHourRaw = (seg.endTime / 60)

            val endHour = endHourRaw.coerceIn(startHour + 1, 24)
            seg.copy(startTime = startHour, endTime = endHour)
        }
        .sortedBy { it.startTime }

    val hourly = mutableListOf<CarelevoBasalSegmentDomainModel>()

    for (hour in 0..23) {
        val seg = normalized.lastOrNull { it.startTime <= hour && hour < it.endTime }
        hourly.add(
            CarelevoBasalSegmentDomainModel(
                startTime = hour,
                endTime = hour + 1,
                speed = seg?.speed ?: 0.0
            )
        )
    }

    return hourly
}

internal fun generateUUID(): String {
    return UUID.randomUUID().toString()
}