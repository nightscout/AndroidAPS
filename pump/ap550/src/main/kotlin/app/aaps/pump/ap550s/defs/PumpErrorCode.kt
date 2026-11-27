package app.aaps.pump.ap550s.defs

import app.aaps.pump.ap550s.R

enum class PumpErrorCode(var resourceId: Int) {

    NomalInject(R.string.deliver_treatment_error_code_00),
    PumpStopDeliver(R.string.deliver_treatment_error_code_01),
    PumpRefill(R.string.deliver_treatment_error_code_02),
    PumpAbnormalmotor(R.string.deliver_treatment_error_code_03),
    PumpAbnormalBattery(R.string.deliver_treatment_error_code_04),
    PumpDosageExhaustion(R.string.deliver_treatment_error_code_05),
    PumpPipelineBlockage(R.string.deliver_treatment_error_code_06),
    ReservoirEmpty(R.string.deliver_treatment_error_code_07),
    PipelineDetachment(R.string.deliver_treatment_error_code_08),
    ExceedsMaximum(R.string.deliver_treatment_error_code_09),
    CommunicationBreak(R.string.deliver_treatment_error_code_12),
    BolusInjecting(R.string.deliver_treatment_bolus_error_code_10),
    TempBasalInjecting(R.string.deliver_treatment_temp_basal_error_code_10);

}