package app.aaps.pump.insight.descriptors

enum class AlertType(val id: Int, val incId: Int) {
    REMINDER_01(31, 1),
    REMINDER_02(227, 2),
    REMINDER_03(252, 3),
    REMINDER_04(805, 4),
    REMINDER_07(826, 7),
    WARNING_31(966, 31),
    WARNING_32(985, 32),
    WARNING_33(1354, 33),
    WARNING_34(1365, 34),
    WARNING_36(1449, 36),
    WARNING_38(1462, 38),
    WARNING_39(1647, 39),
    MAINTENANCE_20(1648, 20),
    MAINTENANCE_21(1676, 21),
    MAINTENANCE_22(1683, 22),
    MAINTENANCE_23(6182, 23),
    MAINTENANCE_24(6201, 24),
    MAINTENANCE_25(6341, 25),
    MAINTENANCE_26(6362, 26),
    MAINTENANCE_27(6915, 27),
    MAINTENANCE_28(6940, 28),
    MAINTENANCE_29(7136, 29),
    MAINTENANCE_30(7167, 30),
    ERROR_6(7532, 6),
    ERROR_10(7539, 10),
    ERROR_13(7567, 13);

    companion object {

        fun fromId(id: Int) = AlertType.entries.firstOrNull { it.id == id }
        fun fromIncId(incId: Int) = AlertType.entries.firstOrNull { it.incId == incId }
    }
}