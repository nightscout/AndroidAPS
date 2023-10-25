package info.nightscout.pump.medtrum.comm.enums

enum class AlarmState {
    NONE,
    PUMP_LOW_BATTERY,       // Mapped from error flag 1
    PUMP_LOW_RESERVOIR,     // Mapped from error flag 2
    PUMP_EXPIRES_SOON,      // Mapped from error flag 3
    LOW_BG_SUSPENDED,       // Mapped from pump status 64
    LOW_BG_SUSPENDED2,      // Mapped from pump status 65
    AUTO_SUSPENDED,         // Mapped from pump status 66
    HOURLY_MAX_SUSPENDED,   // Mapped from pump status 67
    DAILY_MAX_SUSPENDED,    // Mapped from pump status 68
    SUSPENDED,              // Mapped from pump status 69
    PAUSED,                 // Mapped from pump status 70
    OCCLUSION,              // Mapped from pump status 96
    EXPIRED,                // Mapped from pump status 97
    RESERVOIR_EMPTY,        // Mapped from pump status 98
    PATCH_FAULT,            // Mapped from pump status 99
    PATCH_FAULT2,           // Mapped from pump status 100
    BASE_FAULT,             // Mapped from pump status 101
    BATTERY_OUT,            // Mapped from pump status 102
    NO_CALIBRATION          // Mapped from pump status 103
}
