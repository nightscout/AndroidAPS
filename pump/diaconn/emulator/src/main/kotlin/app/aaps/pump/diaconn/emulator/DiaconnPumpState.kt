package app.aaps.pump.diaconn.emulator

import java.io.Serializable

/**
 * All a Diaconn G8 holds. Plain fields, so a test can set up any pump before it starts. [DiaconnPumpEmulator] changes it
 * only while it holds the lock of this object.
 *
 * Java serialization of this object is the saved emulator (`DiaconnPumpEmulator.saveState`), with the bolus that runs.
 */
class DiaconnPumpState : Serializable {

    // Who the pump is. Real G8 (2026-11-11), SerialNum answer: '1', '1', 2020-06-09, lot 1, serial 64004, fw 2.50.

    /** An ASCII digit, as the driver reads it. */
    var country: Int = '1'.code

    /** An ASCII digit, as the driver reads it. */
    var productType: Int = '1'.code
    var makeYear: Int = 20
    var makeMonth: Int = 6
    var makeDay: Int = 9
    var lotNumber: Int = 1

    /**
     * A new one for each emulated pump, never a real one. With the serial of a real pump, the emulator sent its log to the
     * Diaconn cloud under that pump's identity, and each test took the pump log position of the test before it.
     */
    var serialNumber: Int = EMULATED_SERIALS.random()

    /** Firmware 2.50. From 3.0 the driver reads the status with BigAPSMainInfo, from 3.53 it shows bolus progress reports. */
    var firmwareMajor: Int = 2
    var firmwareMinor: Int = 50

    /** UNVERIFIED: the number a real pump has. It changes when the pump is reset, and the driver then reads its log anew. */
    var incarnation: Int = 1

    // The clock: the phone clock plus this offset, in whole seconds.

    var clockOffsetMs: Long = 0

    /**
     * The clock was never set. Real G8 (2026-11-11): it answered 2025-03-19 00:65:21 and its screen showed 00:70, so the
     * minutes count from the power-on and never go into the hour. Setting the clock (0x0F) ends it.
     */
    var clockNeverSet: Boolean = false

    /** When the pump was switched on, for a clock that was never set. */
    var poweredOnAt: Long = System.currentTimeMillis()

    // Insulin, battery and limits.

    /** Units left in the reservoir. */
    var reservoir: Double = 200.0

    /** Percent. */
    var battery: Int = 90

    /**
     * The BasalLimit answer in U/h. Real G8 (2026-11-11): 3.00. The driver makes its max basal of 6.0 from it (x2 below
     * fw 3.0, x2.5 from 3.0).
     */
    var basalLimit: Double = 3.0

    /** Real G8 (2026-11-11): 30.00 U at once, 90.00 U a day. */
    var maxBolus: Double = 30.0
    var maxBolusPerDay: Double = 90.0

    /** UNVERIFIED: the value; only BigAPSMainInfo carries it and no real answer of it was seen. */
    var mealLimitTime: Int = 0

    // Basal.

    /** The basic pattern (pattern 1), U/h from midnight. Only this pattern is kept. */
    var basalRates: DoubleArray = DoubleArray(24) { 1.0 }

    /** The active pattern: 1 is the basic pattern. */
    var basalPattern: Int = 1

    /** The user stopped the insulin on the pump (basal pause, status 1 in the answers). */
    var suspended: Boolean = false

    // Settings. UNVERIFIED: the sound, alarm, screen and language values of a new pump.

    /** U/min, 1..8. The driver counts 12 s per unit at speed 5. */
    var bolusSpeed: Int = 5
    var beepAndAlarm: Int = 1
    var alarmIntensity: Int = 1
    var lcdOnTime: Int = 1

    /** 1 Chinese, 2 Korean, 3 English. */
    var language: Int = 3

    // Temp basal. A real G8 keeps time and rate in its answers after the temp basal ended (2026-11-11).

    var tempBasalRunning: Boolean = false

    /** In 15-minute steps, 2..96. */
    var tempBasalTime: Int = 0

    /** 1000 + U/h x 100 for a rate, 50000 + percent for a percent. */
    var tempBasalRateRatio: Int = 0
    var tempBasalStartedAt: Long = 0

    /** The low 4 bytes of the TB_DTTM the app sent with the start. The pump copies it into its records. */
    var tempBasalDttm: Int = 0

    // Boluses.

    var runningBolus: RunningBolus? = null

    /** The extended (square) bolus that runs. */
    var extendedBolus: RunningExtendedBolus? = null

    /** The next bolus stops after this many units, as at an occlusion. */
    var stopNextBolusAt: Double? = null

    /** Every bolus the pump gave, oldest first, with what it gave. */
    val deliveredBoluses: ArrayList<Double> = ArrayList()

    /** The last two boluses, newest first, for the status answers. */
    val recentBoluses: ArrayList<RecentBolus> = ArrayList()

    // Totals, in units.

    var todayBasal: Double = 0.0

    /** Normal and extended boluses of today. */
    var todayBolus: Double = 0.0

    /** The basal of this hour without the temp basal, and with it. */
    var hourBasalBefore: Double = 0.0
    var hourBasalAfter: Double = 0.0

    // The pump log.

    /** The records, at most 10000: a new record replaces the old one with the same number. */
    val log: ArrayList<LogRecord> = ArrayList()

    /**
     * The number the next record gets, 0..9999. LogStatus answers it, which is one past the newest record: a real G8
     * (2026-11-11) answered 4055 with 4054 as its newest record. A test sets it near 9999, before the first record, to
     * see the log wrap.
     */
    var nextLogNumber: Int = 0

    /** How often the numbers went past 9999. It goes up when the number goes from 9999 to 0. */
    var wrappingCount: Int = 0

    /** Requests the pump got since it started. */
    var commands: Int = 0

    /** Up to when the basal, the temp basal end and the extended bolus end were counted. 0 = not yet. */
    var lastSettleAt: Long = 0

    private companion object {

        private const val serialVersionUID = 1L

        /** Serial numbers for emulated pumps. Not 64004, the real G8 whose answers these tests copy. */
        val EMULATED_SERIALS = 10000..19999
    }
}

/**
 * A normal bolus. It starts [startDelayMs] after the confirm at [confirmedAt], then gives 0.01 U every [msPerHundredth].
 * @param stopAtHundredths the pump stops it there by itself, as at an occlusion, or null
 * @param logSeconds the time of its log record, see [DiaconnLog]
 */
class RunningBolus(
    val setHundredths: Int,
    val confirmedAt: Long,
    val startDelayMs: Long,
    val msPerHundredth: Long,
    val stopAtHundredths: Int?,
    val logSeconds: Long
) : Serializable {

    var givenHundredths: Int = 0

    /** Where the bolus ends: the set amount, or the stop of an occlusion. */
    val targetHundredths: Int get() = stopAtHundredths ?: setHundredths

    /** What is given by [now]. */
    fun dueHundredths(now: Long): Int {
        val running = now - confirmedAt - startDelayMs
        return if (running <= 0) 0 else (running / msPerHundredth.coerceAtLeast(1)).coerceAtMost(targetHundredths.toLong()).toInt()
    }

    val endsAt: Long get() = confirmedAt + startDelayMs + targetHundredths * msPerHundredth

    private companion object {

        private const val serialVersionUID = 1L
    }
}

/** An extended (square) bolus of [amountHundredths] over [minutes], from [startedAt]. */
class RunningExtendedBolus(val amountHundredths: Int, val minutes: Int, val startedAt: Long) : Serializable {

    val endsAt: Long get() = startedAt + minutes * 60_000L

    /** What is given by [now], in 0.01 U. */
    fun givenHundredths(now: Long): Int =
        (amountHundredths.toLong() * (now - startedAt).coerceIn(0, minutes * 60_000L) / (minutes * 60_000L)).toInt()

    private companion object {

        private const val serialVersionUID = 1L
    }
}

/** A bolus for the status answers: kind (2 snack, 3 square), time as in the log, amount in 0.01 U. */
class RecentBolus(val kind: Int, val logSeconds: Long, val amountHundredths: Int) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}

/** A record of the pump log: its number 0..9999, the wrapping count it was written with, and its 12 bytes. */
class LogRecord(val number: Int, val wrappingCount: Int, val data: ByteArray) : Serializable {

    private companion object {

        private const val serialVersionUID = 1L
    }
}
