package app.aaps.pump.carelevo.di

import app.aaps.pump.carelevo.data.protocol.command.CarelevoProtocolCommand
import app.aaps.pump.carelevo.data.protocol.command.CarelevoProtocolCommand.Companion.commandToCode
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParserProvider
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParserRegister
import app.aaps.pump.carelevo.data.protocol.parser.basal.CarelevoProtocolAdditionalBasalInfusionChangeParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.basal.CarelevoProtocolAdditionalBasalProgramSetParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.basal.CarelevoProtocolBasalInfusionChangeParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.basal.CarelevoProtocolBasalInfusionResumeParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.basal.CarelevoProtocolBasalProgramSetParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.basal.CarelevoProtocolTempBasalInfusionCancelParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.basal.CarelevoProtocolTempBasalInfusionParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.bolus.CarelevoProtocolBolusInfusionCancelParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.bolus.CarelevoProtocolExtendBolusDelayRptParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.bolus.CarelevoProtocolExtendBolusInfusionCancelParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.bolus.CarelevoProtocolExtendBolusInfusionParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.bolus.CarelevoProtocolImmeBolusInfusionParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolAdditionalPrimingParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolAlertMsgParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolAppAuthAckParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolAppAuthKeyAckParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolAppStatusParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolBuzzUsageChangeParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolCannulaInsertionAckParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolCannulaInsertionStatusParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolGlucoseMeasurementAlarmTimerParerImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolGlucoseTimerForCGMParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolGlucoseTimerParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolInfusionStatusInquiryParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolInfusionThresholdParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolMsgSolutionParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolNoticeMsgParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchAddressParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchAlertAlarmSetParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchBuzzInspectionParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchDiscardParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchExpiryExtendParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchInformationInquiryDetailParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchInformationInquiryParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchInitParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchNoticeThresholdParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchOperationDataParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchRecoveryParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPatchThresholdSetParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPumpResumeParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPumpStopParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolPumpStopRptParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolSafetyCheckParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolTimeSetParserImpl
import app.aaps.pump.carelevo.data.protocol.parser.patch.CarelevoProtocolWarningMsgParserImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CarelevoProtocolParserModule {

    @Provides
    @Singleton
    fun provideCarelevoProtocolParserProvider(): CarelevoProtocolParserProvider {
        return CarelevoProtocolParserProvider()
    }

    @Provides
    fun provideCarelevoProtocolTimeSetParser() = CarelevoProtocolTimeSetParserImpl(CarelevoProtocolCommand.CMD_SET_TIME_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolAppAuthKeyAckParser() = CarelevoProtocolAppAuthKeyAckParserImpl(CarelevoProtocolCommand.CMD_APP_AUTH_KEY_ACK.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolSafetyCheckParser() = CarelevoProtocolSafetyCheckParserImpl(CarelevoProtocolCommand.CMD_SAFETY_CHECK_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolBasalSetParser() = CarelevoProtocolBasalProgramSetParserImpl(CarelevoProtocolCommand.CMD_BASAL_PROGRAM_RES1.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolAdditionalBasalSetParser() = CarelevoProtocolAdditionalBasalProgramSetParserImpl(CarelevoProtocolCommand.CMD_BASAL_PROGRAM_RES2.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchNoticeThresholdParser() = CarelevoProtocolPatchNoticeThresholdParserImpl(CarelevoProtocolCommand.CMD_NOTICE_THRESHOLD_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolInfusionThresholdParser() = CarelevoProtocolInfusionThresholdParserImpl(CarelevoProtocolCommand.CMD_INFUSION_THRESHOLD_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolBuzzUsageChangeParser() = CarelevoProtocolBuzzUsageChangeParserImpl(CarelevoProtocolCommand.CMD_BUZZ_CHANGE_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolCannulaInsertionStatusParser() = CarelevoProtocolCannulaInsertionStatusParserImpl(CarelevoProtocolCommand.CMD_NEEDLE_INSERT_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolCannulaInsertionActParser() = CarelevoProtocolCannulaInsertionAckParserImpl(CarelevoProtocolCommand.CMD_CANNULA_INSERTION_RPT_ACK_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchThresholdSetParser() = CarelevoProtocolPatchThresholdSetParserImpl(CarelevoProtocolCommand.CMD_THRESHOLD_SETUP_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchExpiryExtendParser() = CarelevoProtocolPatchExpiryExtendParserImpl(CarelevoProtocolCommand.CMD_USAGE_TIME_EXTEND_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolBasalInfusionChangeParser() = CarelevoProtocolBasalInfusionChangeParserImpl(CarelevoProtocolCommand.CMD_BASAL_CHANGE_RES1.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolAdditionalBasalInfusionChangeParser() = CarelevoProtocolAdditionalBasalInfusionChangeParserImpl(CarelevoProtocolCommand.CMD_BASAL_CHANGE_RES2.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolTempBasalInfusionParser() = CarelevoProtocolTempBasalInfusionParserImpl(CarelevoProtocolCommand.CMD_TEMP_BASAL_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolImmeBolusInfusionParser() = CarelevoProtocolImmeBolusInfusionParserImpl(CarelevoProtocolCommand.CMD_IMMED_BOLUS_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolExtendBolusInfusionParser() = CarelevoProtocolExtendBolusInfusionParserImpl(CarelevoProtocolCommand.CMD_EXTENDED_BOLUS_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPumpStopParser() = CarelevoProtocolPumpStopParserImpl(CarelevoProtocolCommand.CMD_PUMP_STOP_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPumpResumeParser() = CarelevoProtocolPumpResumeParserImpl(CarelevoProtocolCommand.CMD_PUMP_RESTART_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolBAsalInfusionResumeParser() = CarelevoProtocolBasalInfusionResumeParserImpl(CarelevoProtocolCommand.CMD_BASAL_RESTART_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolExtendBolusInfusionCancelParser() = CarelevoProtocolExtendBolusInfusionCancelParserImpl(CarelevoProtocolCommand.CMD_EXTEND_BOLUS_CANCEL_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPumpStopRptParser() = CarelevoProtocolPumpStopRptParserImpl(CarelevoProtocolCommand.CMD_PUMP_STOP_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolBolusInfusionCancelParser() = CarelevoProtocolBolusInfusionCancelParserImpl(CarelevoProtocolCommand.CMD_BOLUS_CANCEL_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolTempBasalInfusionCancelParser() = CarelevoProtocolTempBasalInfusionCancelParserImpl(CarelevoProtocolCommand.CMD_TEMP_BASAL_CANCEL_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolInfusionStatusInquiryParser() = CarelevoProtocolInfusionStatusInquiryParserImpl(CarelevoProtocolCommand.CMD_INFUSION_INFO_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchInformationInquiryParser() = CarelevoProtocolPatchInformationInquiryParserImpl(CarelevoProtocolCommand.CMD_PATCH_INFO_RPT1.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchInformationInquiryDetailParser() = CarelevoProtocolPatchInformationInquiryDetailParserImpl(CarelevoProtocolCommand.CMD_PATCH_INFO_RPT2.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchDiscardParser() = CarelevoProtocolPatchDiscardParserImpl(CarelevoProtocolCommand.CMD_PATCH_DISCARD_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchBzzInspectionParser() = CarelevoProtocolPatchBuzzInspectionParserImpl(CarelevoProtocolCommand.CMD_BUZZ_CHANGE_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchOperationDataParser() = CarelevoProtocolPatchOperationDataParserImpl(CarelevoProtocolCommand.CMD_PULSE_FINISH_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolAppStatusParser() = CarelevoProtocolAppStatusParserImpl(CarelevoProtocolCommand.CMD_APP_STATUS_ACK.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolGlucoseMeasurementAlarmTimerParser() = CarelevoProtocolGlucoseMeasurementAlarmTimerParerImpl(CarelevoProtocolCommand.CMD_GLUCOSE_MEASUREMENT_ALARM_TIMER_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolGlucoseTimerForCGMParser() = CarelevoProtocolGlucoseTimerForCGMParserImpl(CarelevoProtocolCommand.CMD_GLUCOSE_TIMER_FOR_CGM_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolGlucoseTimerParser() = CarelevoProtocolGlucoseTimerParserImpl(CarelevoProtocolCommand.CMD_GLUCOSE_TIMER_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchAddressParser() = CarelevoProtocolPatchAddressParserImpl(CarelevoProtocolCommand.CMD_MAC_ADDR_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolWarningMsgParser() = CarelevoProtocolWarningMsgParserImpl(CarelevoProtocolCommand.CMD_WARNING_MSG_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolAlertMsgParser() = CarelevoProtocolAlertMsgParserImpl(CarelevoProtocolCommand.CMD_ALERT_MSG_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolNoticeMsgParser() = CarelevoProtocolNoticeMsgParserImpl(CarelevoProtocolCommand.CMD_NOTICE_MSG_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolMsgSolutionParser() = CarelevoProtocolMsgSolutionParserImpl(CarelevoProtocolCommand.CMD_ALARM_CLEAR_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchInitParser() = CarelevoProtocolPatchInitParserImpl(CarelevoProtocolCommand.CMD_PATCH_INIT_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchRecoveryParser() = CarelevoProtocolPatchRecoveryParserImpl(CarelevoProtocolCommand.CMD_PATCH_RECOVERY_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolExtendBolusDelayRptParser() = CarelevoProtocolExtendBolusDelayRptParserImpl(CarelevoProtocolCommand.CMD_INFUSION_DELAY_RPT.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolAdditionalPrimingParser() = CarelevoProtocolAdditionalPrimingParserImpl(CarelevoProtocolCommand.CMD_ADD_PRIMING_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolPatchAlertAlarmSetParser() = CarelevoProtocolPatchAlertAlarmSetParserImpl(CarelevoProtocolCommand.CMD_ALERT_ALARM_SET_RES.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolAppAuthAckParser() = CarelevoProtocolAppAuthAckParserImpl(CarelevoProtocolCommand.CMD_APP_AUTH_ACK.commandToCode().toUByte().toInt())

    @Provides
    fun provideCarelevoProtocolParserRegister(
        carelevoProtocolParserProvider: CarelevoProtocolParserProvider,
        carelevoProtocolPatchAddressParser: CarelevoProtocolPatchAddressParserImpl,
        carelevoProtocolTimeSetParser: CarelevoProtocolTimeSetParserImpl,
        carelevoProtocolAppAuthKeyAckParser: CarelevoProtocolAppAuthKeyAckParserImpl,
        carelevoProtocolSafetyCheckParser: CarelevoProtocolSafetyCheckParserImpl,
        carelevoProtocolBasalProgramSetParser: CarelevoProtocolBasalProgramSetParserImpl,
        carelevoProtocolAdditionalBasalProgramSetParser: CarelevoProtocolAdditionalBasalProgramSetParserImpl,
        carelevoProtocolInfusionThresholdParser: CarelevoProtocolInfusionThresholdParserImpl,
        carelevoProtocolBuzzUsageChangeParser: CarelevoProtocolBuzzUsageChangeParserImpl,
        carelevoProtocolCannulaInsertionStatusParser: CarelevoProtocolCannulaInsertionStatusParserImpl,
        carelevoProtocolCannulaInsertionAckParser: CarelevoProtocolCannulaInsertionAckParserImpl,
        carelevoProtocolPatchThresholdSetParser: CarelevoProtocolPatchThresholdSetParserImpl,
        carelevoProtocolPatchExpiryExtendParser: CarelevoProtocolPatchExpiryExtendParserImpl,
        carelevoProtocolBasalInfusionChangeParser: CarelevoProtocolBasalInfusionChangeParserImpl,
        carelevoProtocolAdditionalBasalInfusionChangeParser: CarelevoProtocolAdditionalBasalInfusionChangeParserImpl,
        carelevoProtocolTempBasalInfusionParser: CarelevoProtocolTempBasalInfusionParserImpl,
        carelevoProtocolImmeBolusInfusionParser: CarelevoProtocolImmeBolusInfusionParserImpl,
        carelevoProtocolExtendBolusInfusionParser: CarelevoProtocolExtendBolusInfusionParserImpl,
        carelevoProtocolPumpStopParser: CarelevoProtocolPumpStopParserImpl,
        carelevoProtocolPumpResumeParser: CarelevoProtocolPumpResumeParserImpl,
        carelevoProtocolBasalInfusionResumeParser: CarelevoProtocolBasalInfusionResumeParserImpl,
        carelevoProtocolExtendBolusInfusionCancelParser: CarelevoProtocolExtendBolusInfusionCancelParserImpl,
        carelevoProtocolPumpStopRptParser: CarelevoProtocolPumpStopRptParserImpl,
        carelevoProtocolBolusInfusionCancelParser: CarelevoProtocolBolusInfusionCancelParserImpl,
        carelevoProtocolTempBasalInfusionCancelParser: CarelevoProtocolTempBasalInfusionCancelParserImpl,
        carelevoProtocolInfusionStatusInquiryParser: CarelevoProtocolInfusionStatusInquiryParserImpl,
        carelevoProtocolPatchInformationInquiryParser: CarelevoProtocolPatchInformationInquiryParserImpl,
        carelevoProtocolPatchInformationInquiryDetailParser: CarelevoProtocolPatchInformationInquiryDetailParserImpl,
        carelevoProtocolPatchDiscardParser: CarelevoProtocolPatchDiscardParserImpl,
        carelevoProtocolPatchBuzzInspectionParser: CarelevoProtocolPatchBuzzInspectionParserImpl,
        carelevoProtocolPatchOperationDataParser: CarelevoProtocolPatchOperationDataParserImpl,
        carelevoProtocolAppStatusParser: CarelevoProtocolAppStatusParserImpl,
        carelevoProtocolExtendBolusDelayRptParser: CarelevoProtocolExtendBolusDelayRptParserImpl,
        carelevoProtocolPatchInitParser: CarelevoProtocolPatchInitParserImpl,
        carelevoProtocolPatchRecoveryParser: CarelevoProtocolPatchRecoveryParserImpl,
        carelevoProtocolWarningMsgParser: CarelevoProtocolWarningMsgParserImpl,
        carelevoProtocolAlertMsgParser: CarelevoProtocolAlertMsgParserImpl,
        carelevoProtocolNoticeMsgParser: CarelevoProtocolNoticeMsgParserImpl,
        carelevoProtocolMsgSolutionParser: CarelevoProtocolMsgSolutionParserImpl,
        carelevoProtocolAdditionalPrimingParser: CarelevoProtocolAdditionalPrimingParserImpl,
        carelevoProtocolPatchNoticeThresholdParser: CarelevoProtocolPatchNoticeThresholdParserImpl,
        carelevoProtocolPatchAlertAlarmSetParser: CarelevoProtocolPatchAlertAlarmSetParserImpl,
        carelevoProtocolAppAuthAckParser: CarelevoProtocolAppAuthAckParserImpl
    ): CarelevoProtocolParserRegister {
        return CarelevoProtocolParserRegister(
            carelevoProtocolParserProvider,
            carelevoProtocolPatchAddressParser,
            carelevoProtocolTimeSetParser,
            carelevoProtocolAppAuthKeyAckParser,
            carelevoProtocolSafetyCheckParser,
            carelevoProtocolBasalProgramSetParser,
            carelevoProtocolAdditionalBasalProgramSetParser,
            carelevoProtocolInfusionThresholdParser,
            carelevoProtocolBuzzUsageChangeParser,
            carelevoProtocolCannulaInsertionStatusParser,
            carelevoProtocolCannulaInsertionAckParser,
            carelevoProtocolPatchThresholdSetParser,
            carelevoProtocolPatchExpiryExtendParser,
            carelevoProtocolBasalInfusionChangeParser,
            carelevoProtocolAdditionalBasalInfusionChangeParser,
            carelevoProtocolTempBasalInfusionParser,
            carelevoProtocolImmeBolusInfusionParser,
            carelevoProtocolExtendBolusInfusionParser,
            carelevoProtocolPumpStopParser,
            carelevoProtocolPumpResumeParser,
            carelevoProtocolBasalInfusionResumeParser,
            carelevoProtocolExtendBolusInfusionCancelParser,
            carelevoProtocolPumpStopRptParser,
            carelevoProtocolBolusInfusionCancelParser,
            carelevoProtocolTempBasalInfusionCancelParser,
            carelevoProtocolInfusionStatusInquiryParser,
            carelevoProtocolPatchInformationInquiryParser,
            carelevoProtocolPatchInformationInquiryDetailParser,
            carelevoProtocolPatchDiscardParser,
            carelevoProtocolPatchBuzzInspectionParser,
            carelevoProtocolPatchOperationDataParser,
            carelevoProtocolAppStatusParser,
            carelevoProtocolExtendBolusDelayRptParser,
            carelevoProtocolPatchInitParser,
            carelevoProtocolPatchRecoveryParser,
            carelevoProtocolWarningMsgParser,
            carelevoProtocolAlertMsgParser,
            carelevoProtocolNoticeMsgParser,
            carelevoProtocolMsgSolutionParser,
            carelevoProtocolAdditionalPrimingParser,
            carelevoProtocolPatchNoticeThresholdParser,
            carelevoProtocolPatchAlertAlarmSetParser,
            carelevoProtocolAppAuthAckParser
        )
    }
}
