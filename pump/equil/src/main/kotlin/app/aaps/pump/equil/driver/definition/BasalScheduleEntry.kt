package app.aaps.pump.equil.driver.definition

import org.joda.time.Duration

data class BasalScheduleEntry(val rate: Double, val startTime: Duration)