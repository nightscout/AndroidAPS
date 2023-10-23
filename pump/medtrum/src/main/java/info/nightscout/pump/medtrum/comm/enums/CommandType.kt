package info.nightscout.pump.medtrum.comm.enums

enum class CommandType(val code: Byte) {
    SYNCHRONIZE(3),
    SUBSCRIBE(4),
    AUTH_REQ(5),
    GET_DEVICE_TYPE(6),
    SET_TIME(10),
    GET_TIME(11),
    SET_TIME_ZONE(12),
    PRIME(16),
    ACTIVATE(18),
    SET_BOLUS(19),
    CANCEL_BOLUS(20),
    SET_BASAL_PROFILE(21),
    SET_TEMP_BASAL(24),
    CANCEL_TEMP_BASAL(25),
    RESUME_PUMP(29),
    POLL_PATCH(30),
    STOP_PATCH(31),
    READ_BOLUS_STATE(34),
    SET_PATCH(35),
    SET_BOLUS_MOTOR(36),
    GET_RECORD(99),
    CLEAR_ALARM(115)
}
