package app.aaps.pump.carelevo.data.protocol.parser

import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalBasalInfusionChangeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalBasalProgramSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAdditionalPrimingRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAlertMsgRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppAuthKeyAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolAppStatusRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalInfusionChangeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalInfusionResumeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBasalProgramSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBolusInfusionCancelRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolBuzzUsageChangeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionAckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolCannulaInsertionStatusRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolExtendBolusDelayRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionCancelRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolExtendBolusInfusionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolImmeBolusInfusionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolInfusionStatusInquiryRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolInfusionThresholdRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolMsgSolutionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolNoticeMsgRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolNoticeThresholdRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchAddressRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchAlertAlarmSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchBuzzInspectionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchDiscardRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchExpiryExtendRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchInformationInquiryDetailRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchInformationInquiryRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchInitRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchOperationDataRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchRecoveryRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPatchThresholdSetRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPumpResumeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPumpStopRptModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolPumpStopRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSafetyCheckRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSetTimeRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionCancelRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolTempBasalInfusionRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolWarningMsgRptModel
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
import javax.inject.Inject

class CarelevoProtocolParserRegister @Inject constructor(
    private val parserProvider: CarelevoProtocolParserProvider,
    private val macAddressParser: CarelevoProtocolPatchAddressParserImpl,
    private val timeSetParser: CarelevoProtocolTimeSetParserImpl,
    private val appAuthKeyAckParser: CarelevoProtocolAppAuthKeyAckParserImpl,
    private val safetyCheckParser: CarelevoProtocolSafetyCheckParserImpl,
    private val basalProgram1Parser: CarelevoProtocolBasalProgramSetParserImpl,
    private val basalProgram2Parser: CarelevoProtocolAdditionalBasalProgramSetParserImpl,
    private val infusionThresholdParser: CarelevoProtocolInfusionThresholdParserImpl,
    private val buzzChangeParser: CarelevoProtocolBuzzUsageChangeParserImpl,
    private val needleInsertParser: CarelevoProtocolCannulaInsertionStatusParserImpl,
    private val needleInsertAckParser: CarelevoProtocolCannulaInsertionAckParserImpl,
    private val thresholdSetupParser: CarelevoProtocolPatchThresholdSetParserImpl,
    private val usageTimeExtendParser: CarelevoProtocolPatchExpiryExtendParserImpl,
    private val basalChange1Parser: CarelevoProtocolBasalInfusionChangeParserImpl,
    private val basalChange2Parser: CarelevoProtocolAdditionalBasalInfusionChangeParserImpl,
    private val tempBasalParser: CarelevoProtocolTempBasalInfusionParserImpl,
    private val immeBolusParser: CarelevoProtocolImmeBolusInfusionParserImpl,
    private val extendBolusParser: CarelevoProtocolExtendBolusInfusionParserImpl,
    private val pumpStopParser: CarelevoProtocolPumpStopParserImpl,
    private val pumpRestartParser: CarelevoProtocolPumpResumeParserImpl,
    private val basalRestartParser: CarelevoProtocolBasalInfusionResumeParserImpl,
    private val extendBolusCancelParser: CarelevoProtocolExtendBolusInfusionCancelParserImpl,
    private val pumpStopRptParser: CarelevoProtocolPumpStopRptParserImpl,
    private val bolusCancelParser: CarelevoProtocolBolusInfusionCancelParserImpl,
    private val tempBasalCancelParser: CarelevoProtocolTempBasalInfusionCancelParserImpl,
    private val infusionInfoParser: CarelevoProtocolInfusionStatusInquiryParserImpl,
    private val patchInfo1Parser: CarelevoProtocolPatchInformationInquiryParserImpl,
    private val patchInfo2Parser: CarelevoProtocolPatchInformationInquiryDetailParserImpl,
    private val discardParser: CarelevoProtocolPatchDiscardParserImpl,
    private val buzzerCheckParser: CarelevoProtocolPatchBuzzInspectionParserImpl,
    private val pulseFinishParser: CarelevoProtocolPatchOperationDataParserImpl,
    private val appStatusAckParser: CarelevoProtocolAppStatusParserImpl,
    private val infusionDelayParser: CarelevoProtocolExtendBolusDelayRptParserImpl,
    private val initParser: CarelevoProtocolPatchInitParserImpl,
    private val recoveryParser: CarelevoProtocolPatchRecoveryParserImpl,
    private val msgWarningParser: CarelevoProtocolWarningMsgParserImpl,
    private val msgAlertParser: CarelevoProtocolAlertMsgParserImpl,
    private val msgNoticeParser: CarelevoProtocolNoticeMsgParserImpl,
    private val alarmSolveParser: CarelevoProtocolMsgSolutionParserImpl,
    private val additionalPrimingParser: CarelevoProtocolAdditionalPrimingParserImpl,
    private val noticeThresholdParser: CarelevoProtocolPatchNoticeThresholdParserImpl,
    private val patchAlertSetParser: CarelevoProtocolPatchAlertAlarmSetParserImpl,
    private val appAuthAckParser: CarelevoProtocolAppAuthAckParserImpl
) {

    fun registerParser() {
        parserProvider.apply {
            registerParser(ProtocolPatchAddressRspModel::class.java, macAddressParser)
            registerParser(ProtocolAppAuthKeyAckRspModel::class.java, appAuthKeyAckParser)
            registerParser(ProtocolSetTimeRspModel::class.java, timeSetParser)
            registerParser(ProtocolSafetyCheckRspModel::class.java, safetyCheckParser)
            registerParser(ProtocolBasalProgramSetRspModel::class.java, basalProgram1Parser)
            registerParser(ProtocolAdditionalBasalProgramSetRspModel::class.java, basalProgram2Parser)
            registerParser(ProtocolInfusionThresholdRspModel::class.java, infusionThresholdParser)
            registerParser(ProtocolBuzzUsageChangeRspModel::class.java, buzzChangeParser)
            registerParser(ProtocolCannulaInsertionStatusRspModel::class.java, needleInsertParser)
            registerParser(ProtocolCannulaInsertionAckRspModel::class.java, needleInsertAckParser)
            registerParser(ProtocolPatchThresholdSetRspModel::class.java, thresholdSetupParser)
            registerParser(ProtocolPatchExpiryExtendRspModel::class.java, usageTimeExtendParser)
            registerParser(ProtocolBasalInfusionChangeRspModel::class.java, basalChange1Parser)
            registerParser(ProtocolAdditionalBasalInfusionChangeRspModel::class.java, basalChange2Parser)
            registerParser(ProtocolTempBasalInfusionRspModel::class.java, tempBasalParser)
            registerParser(ProtocolImmeBolusInfusionRspModel::class.java, immeBolusParser)
            registerParser(ProtocolExtendBolusInfusionRspModel::class.java, extendBolusParser)
            registerParser(ProtocolPumpStopRspModel::class.java, pumpStopParser)
            registerParser(ProtocolPumpResumeRspModel::class.java, pumpRestartParser)
            registerParser(ProtocolBasalInfusionResumeRspModel::class.java, basalRestartParser)
            registerParser(ProtocolExtendBolusInfusionCancelRspModel::class.java, extendBolusCancelParser)
            registerParser(ProtocolPumpStopRptModel::class.java, pumpStopRptParser)
            registerParser(ProtocolBolusInfusionCancelRspModel::class.java, bolusCancelParser)
            registerParser(ProtocolTempBasalInfusionCancelRspModel::class.java, tempBasalCancelParser)
            registerParser(ProtocolInfusionStatusInquiryRptModel::class.java, infusionInfoParser)
            registerParser(ProtocolPatchInformationInquiryRptModel::class.java, patchInfo1Parser)
            registerParser(ProtocolPatchInformationInquiryDetailRptModel::class.java, patchInfo2Parser)
            registerParser(ProtocolPatchDiscardRspModel::class.java, discardParser)
            registerParser(ProtocolPatchBuzzInspectionRspModel::class.java, buzzerCheckParser)
            registerParser(ProtocolPatchOperationDataRspModel::class.java, pulseFinishParser)
            registerParser(ProtocolAppStatusRspModel::class.java, appStatusAckParser)
            registerParser(ProtocolExtendBolusDelayRptModel::class.java, infusionDelayParser)
            registerParser(ProtocolPatchInitRspModel::class.java, initParser)
            registerParser(ProtocolPatchRecoveryRptModel::class.java, recoveryParser)
            registerParser(ProtocolWarningMsgRptModel::class.java, msgWarningParser)
            registerParser(ProtocolAlertMsgRptModel::class.java, msgAlertParser)
            registerParser(ProtocolNoticeMsgRptModel::class.java, msgNoticeParser)
            registerParser(ProtocolMsgSolutionRspModel::class.java, alarmSolveParser)
            registerParser(ProtocolAdditionalPrimingRspModel::class.java, additionalPrimingParser)
            registerParser(ProtocolNoticeThresholdRspModel::class.java, noticeThresholdParser)
            registerParser(ProtocolPatchAlertAlarmSetRspModel::class.java, patchAlertSetParser)
            registerParser(ProtocolAppAuthAckRspModel::class.java, appAuthAckParser)
        }
    }
}