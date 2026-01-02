package app.aaps.pump.omnipod.eros.driver.definition;

import java.util.Locale;

public enum FaultEventCode {
    FAILED_FLASH_ERASE((byte) 0x01),
    FAILED_FLASH_STORE((byte) 0x02),
    TABLE_CORRUPTION_BASAL_SUBCOMMAND((byte) 0x03),
    CORRUPTION_BYTE_720((byte) 0x05),
    DATA_CORRUPTION_IN_TEST_RTC_INTERRUPT((byte) 0x06),
    RTC_INTERRUPT_HANDLER_INCONSISTENT_STATE((byte) 0x07),
    VALUE_GREATER_THAN_8((byte) 0x08),
    BF_0_NOT_EQUAL_TO_BF_1((byte) 0x0A),
    TABLE_CORRUPTION_TEMP_BASAL_SUBCOMMAND((byte) 0x0B),
    RESET_DUE_TO_COP((byte) 0x0D),
    RESET_DUE_TO_ILLEGAL_OPCODE((byte) 0x0E),
    RESET_DUE_TO_ILLEGAL_ADDRESS((byte) 0x0F),
    RESET_DUE_TO_SAWCOP((byte) 0x10),
    CORRUPTION_IN_BYTE_866((byte) 0x11),
    RESET_DUE_TO_LVD((byte) 0x12),
    MESSAGE_LENGTH_TOO_LONG((byte) 0x13),
    OCCLUDED((byte) 0x14),
    CORRUPTION_IN_WORD_129((byte) 0x15),
    CORRUPTION_IN_BYTE_868((byte) 0x16),
    CORRUPTION_IN_A_VALIDATED_TABLE((byte) 0x17),
    RESERVOIR_EMPTY((byte) 0x18),
    BAD_POWER_SWITCH_ARRAY_VALUE_1((byte) 0x19),
    BAD_POWER_SWITCH_ARRAY_VALUE_2((byte) 0x1A),
    BAD_LOAD_CNTH_VALUE((byte) 0x1B),
    EXCEEDED_MAXIMUM_POD_LIFE_80_HRS((byte) 0x1C),
    BAD_STATE_COMMAND_1_A_SCHEDULE_PARSE((byte) 0x1D),
    UNEXPECTED_STATE_IN_REGISTER_UPON_RESET((byte) 0x1E),
    WRONG_SUMMARY_FOR_TABLE_129((byte) 0x1F),
    VALIDATE_COUNT_ERROR_WHEN_BOLUSING((byte) 0x20),
    BAD_TIMER_VARIABLE_STATE((byte) 0x21),
    UNEXPECTED_RTC_MODULE_VALUE_DURING_RESET((byte) 0x22),
    PROBLEM_CALIBRATE_TIMER((byte) 0x23),
    RTC_INTERRUPT_HANDLER_UNEXPECTED_CALL((byte) 0x26),
    MISSING_2_HOUR_ALERT_TO_FILL_TANK((byte) 0x27),
    FAULT_EVENT_SETUP_POD((byte) 0x28),
    ERROR_MAIN_LOOP_HELPER_0((byte) 0x29),
    ERROR_MAIN_LOOP_HELPER_1((byte) 0x2A),
    ERROR_MAIN_LOOP_HELPER_2((byte) 0x2B),
    ERROR_MAIN_LOOP_HELPER_3((byte) 0x2C),
    ERROR_MAIN_LOOP_HELPER_4((byte) 0x2D),
    ERROR_MAIN_LOOP_HELPER_5((byte) 0x2E),
    ERROR_MAIN_LOOP_HELPER_6((byte) 0x2F),
    ERROR_MAIN_LOOP_HELPER_7((byte) 0x30),
    INSULIN_DELIVERY_COMMAND_ERROR((byte) 0x31),
    BAD_VALUE_STARTUP_TEST((byte) 0x32),
    CONNECTED_POD_COMMAND_TIMEOUT((byte) 0x33),
    RESET_FROM_UNKNOWN_CAUSE((byte) 0x34),
    ERROR_FLASH_INITIALIZATION((byte) 0x36),
    BAD_PIEZO_VALUE((byte) 0x37),
    UNEXPECTED_VALUE_BYTE_358((byte) 0x38),
    PROBLEM_WITH_LOAD_1_AND_2((byte) 0x39),
    A_GREATER_THAN_7_IN_MESSAGE((byte) 0x3A),
    FAILED_TEST_SAW_RESET((byte) 0x3B),
    TEST_IN_PROGRESS((byte) 0x3C),
    PROBLEM_WITH_PUMP_ANCHOR((byte) 0x3D),
    ERROR_FLASH_WRITE((byte) 0x3E),
    ENCODER_COUNT_TOO_HIGH((byte) 0x40),
    ENCODER_COUNT_EXCESSIVE_VARIANCE((byte) 0x41),
    ENCODER_COUNT_TOO_LOW((byte) 0x42),
    ENCODER_COUNT_PROBLEM((byte) 0x43),
    CHECK_VOLTAGE_OPEN_WIRE_1((byte) 0x44),
    CHECK_VOLTAGE_OPEN_WIRE_2((byte) 0x45),
    PROBLEM_WITH_LOAD_1_AND_2_TYPE_46((byte) 0x46),
    PROBLEM_WITH_LOAD_1_AND_2_TYPE_47((byte) 0x47),
    BAD_TIMER_CALIBRATION((byte) 0x48),
    BAD_TIMER_RATIOS((byte) 0x49),
    BAD_TIMER_VALUES((byte) 0x4A),
    TRIM_ICS_TOO_CLOSE_TO_0_X_1_FF((byte) 0x4B),
    PROBLEM_FINDING_BEST_TRIM_VALUE((byte) 0x4C),
    BAD_SET_TPM_1_MULTI_CASES_VALUE((byte) 0x4D),
    UNEXPECTED_RF_ERROR_FLAG_DURING_RESET((byte) 0x4F),
    BAD_CHECK_SDRH_AND_BYTE_11_F_STATE((byte) 0x51),
    ISSUE_TXO_KPROCESS_INPUT_BUFFER((byte) 0x52),
    WRONG_VALUE_WORD_107((byte) 0x53),
    PACKET_FRAME_LENGTH_TOO_LONG((byte) 0x54),
    UNEXPECTED_IRQ_HIGHIN_TIMER_TICK((byte) 0x55),
    UNEXPECTED_IRQ_LOWIN_TIMER_TICK((byte) 0x56),
    BAD_ARG_TO_GET_ENTRY((byte) 0x57),
    BAD_ARG_TO_UPDATE_37_A_TABLE((byte) 0x58),
    ERROR_UPDATING_37_A_TABLE((byte) 0x59),
    OCCLUSION_CHECK_VALUE_TOO_HIGH((byte) 0x5A),
    LOAD_TABLE_CORRUPTION((byte) 0x5B),
    PRIME_OPEN_COUNT_TOO_LOW((byte) 0x5C),
    BAD_VALUE_BYTE_109((byte) 0x5D),
    DISABLE_FLASH_SECURITY_FAILED((byte) 0x5E),
    CHECK_VOLTAGE_FAILURE((byte) 0x5F),
    OCCLUSION_CHECK_STARTUP_1((byte) 0x60),
    OCCLUSION_CHECK_STARTUP_2((byte) 0x61),
    OCCLUSION_CHECK_TIMEOUTS_1((byte) 0x62),
    OCCLUSION_CHECK_TIMEOUTS_2((byte) 0x66),
    OCCLUSION_CHECK_TIMEOUTS_3((byte) 0x67),
    OCCLUSION_CHECK_PULSE_ISSUE((byte) 0x68),
    OCCLUSION_CHECK_BOLUS_PROBLEM((byte) 0x69),
    OCCLUSION_CHECK_ABOVE_THRESHOLD((byte) 0x6A),
    BASAL_UNDER_INFUSION((byte) 0x80),
    BASAL_OVER_INFUSION((byte) 0x81),
    TEMP_BASAL_UNDER_INFUSION((byte) 0x82),
    TEMP_BASAL_OVER_INFUSION((byte) 0x83),
    BOLUS_UNDER_INFUSION((byte) 0x84),
    BOLUS_OVER_INFUSION((byte) 0x85),
    BASAL_OVER_INFUSION_PULSE((byte) 0x86),
    TEMP_BASAL_OVER_INFUSION_PULSE((byte) 0x87),
    BOLUS_OVER_INFUSION_PULSE((byte) 0x88),
    IMMEDIATE_BOLUS_OVER_INFUSION_PULSE((byte) 0x89),
    EXTENDED_BOLUS_OVER_INFUSION_PULSE((byte) 0x8A),
    CORRUPTION_OF_TABLES((byte) 0x8B),
    BAD_INPUT_TO_VERIFY_AND_START_PUMP((byte) 0x8D),
    BAD_PUMP_REQ_5_STATE((byte) 0x8E),
    COMMAND_1_A_PARSE_UNEXPECTED_FAILED((byte) 0x8F),
    BAD_VALUE_FOR_TABLES((byte) 0x90),
    BAD_PUMP_REQ_1_STATE((byte) 0x91),
    BAD_PUMP_REQ_2_STATE((byte) 0x92),
    BAD_PUMP_REQ_3_STATE((byte) 0x93),
    BAD_VALUE_FIELD_6_IN_0_X_1_A((byte) 0x95),
    BAD_STATE_IN_CLEAR_BOLUS_IST_2_AND_VARS((byte) 0x96),
    BAD_STATE_IN_MAYBE_INC_33_D((byte) 0x97),
    VALUES_DO_NOT_MATCH_OR_ARE_GREATER_THAN_0_X_97((byte) 0x98);

    private final byte value;

    FaultEventCode(byte value) {
        this.value = value;
    }

    public static FaultEventCode fromByte(byte value) {
        if (value == 0x00) { // No faults
            return null;
        }
        for (FaultEventCode type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown FaultEventCode: " + value);
    }

    public byte getValue() {
        return value;
    }

    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "Pod fault (%d): %s", value, name());
    }
}
