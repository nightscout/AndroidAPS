package app.aaps.pump.insight.descriptors

import app.aaps.pump.insight.exceptions.app_layer_errors.AlreadyConnectedException
import app.aaps.pump.insight.exceptions.app_layer_errors.AppLayerErrorException
import app.aaps.pump.insight.exceptions.app_layer_errors.BolusAmountNotInRangeException
import app.aaps.pump.insight.exceptions.app_layer_errors.BolusDurationNotInRangeException
import app.aaps.pump.insight.exceptions.app_layer_errors.BolusLagTimeFeatureDisabledException
import app.aaps.pump.insight.exceptions.app_layer_errors.BolusTypeAndParameterMismatchException
import app.aaps.pump.insight.exceptions.app_layer_errors.CommandExecutionFailedException
import app.aaps.pump.insight.exceptions.app_layer_errors.ConfigMemoryAccessException
import app.aaps.pump.insight.exceptions.app_layer_errors.CustomBolusNotConfiguredException
import app.aaps.pump.insight.exceptions.app_layer_errors.ImplausiblePortionLengthValueException
import app.aaps.pump.insight.exceptions.app_layer_errors.IncompatibleVersionException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidAlertInstanceIdException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidConfigBlockCRCException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidConfigBlockIdException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidConfigBlockLengthException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidDateParameterException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidDurationPresetException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidLagTimeException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidParameterTypeException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidPayloadCRCException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidPayloadException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidPayloadLengthException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidServicePasswordException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidTBRDurationException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidTBRFactorException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidTBRTemplateException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidTimeParameterException
import app.aaps.pump.insight.exceptions.app_layer_errors.InvalidValuesOfTwoChannelTransmission
import app.aaps.pump.insight.exceptions.app_layer_errors.MaximumNumberOfBolusTypeAlreadyRunningException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoActiveTBRToCancelException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoActiveTBRToChangeException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoConfigBlockDataException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoServicePasswordNeededException
import app.aaps.pump.insight.exceptions.app_layer_errors.NoSuchBolusToCancelException
import app.aaps.pump.insight.exceptions.app_layer_errors.NotAllowedToAccessPositionZeroException
import app.aaps.pump.insight.exceptions.app_layer_errors.NotConnectedException
import app.aaps.pump.insight.exceptions.app_layer_errors.NotReferencedException
import app.aaps.pump.insight.exceptions.app_layer_errors.PauseModeNotAllowedException
import app.aaps.pump.insight.exceptions.app_layer_errors.PositionProtectedException
import app.aaps.pump.insight.exceptions.app_layer_errors.PumpAlreadyInThatStateException
import app.aaps.pump.insight.exceptions.app_layer_errors.PumpBusyException
import app.aaps.pump.insight.exceptions.app_layer_errors.PumpStoppedException
import app.aaps.pump.insight.exceptions.app_layer_errors.ReadingHistoryAlreadyStartedException
import app.aaps.pump.insight.exceptions.app_layer_errors.ReadingHistoryNotStartedException
import app.aaps.pump.insight.exceptions.app_layer_errors.RunModeNotAllowedException
import app.aaps.pump.insight.exceptions.app_layer_errors.ServiceAlreadyActivatedException
import app.aaps.pump.insight.exceptions.app_layer_errors.ServiceCommandNotAvailableException
import app.aaps.pump.insight.exceptions.app_layer_errors.ServiceIncompatibleException
import app.aaps.pump.insight.exceptions.app_layer_errors.ServiceNotActivatedException
import app.aaps.pump.insight.exceptions.app_layer_errors.StepCountOutOfRangeException
import app.aaps.pump.insight.exceptions.app_layer_errors.UnknownCommandException
import app.aaps.pump.insight.exceptions.app_layer_errors.UnknownServiceException
import app.aaps.pump.insight.exceptions.app_layer_errors.WriteSessionAlreadyOpenException
import app.aaps.pump.insight.exceptions.app_layer_errors.WriteSessionClosedException
import app.aaps.pump.insight.exceptions.app_layer_errors.WrongStateException

class AppErrors(val id: Int, val type: Class<out AppLayerErrorException?>) {

    companion object {

        fun fromId(id: Int): Class<out AppLayerErrorException?>? = when (id) {
            PUMPSTOPPEDEXCEPTION                            -> PumpStoppedException::class.java
            BOLUSAMOUNTNOTINRANGEEXCEPTION                  -> BolusAmountNotInRangeException::class.java
            PUMPALREADYINTHATSTATEEXCEPTION                 -> PumpAlreadyInThatStateException::class.java
            INVALIDSERVICEPASSWORDEXCEPTION                 -> InvalidServicePasswordException::class.java
            UNKNOWNCOMMANDEXCEPTION                         -> UnknownCommandException::class.java
            ALREADYCONNECTEDEXCEPTION                       -> AlreadyConnectedException::class.java
            WRONGSTATEEXCEPTION                             -> WrongStateException::class.java
            SERVICEINCOMPATIBLEEXCEPTION                    -> ServiceIncompatibleException::class.java
            UNKNOWNSERVICEEXCEPTION                         -> UnknownServiceException::class.java
            NOSERVICEPASSWORDNEEDEDEXCEPTION                -> NoServicePasswordNeededException::class.java
            SERVICEALREADYACTIVATEDEXCEPTION                -> ServiceAlreadyActivatedException::class.java
            INCOMPATIBLEVERSIONEXCEPTION                    -> IncompatibleVersionException::class.java
            INVALIDPAYLOADLENGTHEXCEPTION                   -> InvalidPayloadLengthException::class.java
            NOTCONNECTEDEXCEPTION                           -> NotConnectedException::class.java
            SERVICECOMMANDNOTAVAILABLEEXCEPTION             -> ServiceCommandNotAvailableException::class.java
            SERVICENOTACTIVATEDEXCEPTION                    -> ServiceNotActivatedException::class.java
            PUMPBUSYEXCEPTION                               -> PumpBusyException::class.java
            NOTREFERENCEDEXCEPTION                          -> NotReferencedException::class.java
            STEPCOUNTOUTOFRANGEEXCEPTION                    -> StepCountOutOfRangeException::class.java
            INVALIDPAYLOADCRCEXCEPTION                      -> InvalidPayloadCRCException::class.java
            INVALIDPARAMETERTYPEEXCEPTION                   -> InvalidParameterTypeException::class.java
            COMMANDEXECUTIONFAILEDEXCEPTION                 -> CommandExecutionFailedException::class.java
            INVALIDALERTINSTANCEIDEXCEPTION                 -> InvalidAlertInstanceIdException::class.java
            INVALIDTBRFACTOREXCEPTION                       -> InvalidTBRFactorException::class.java
            INVALIDTBRDURATIONEXCEPTION                     -> InvalidTBRDurationException::class.java
            INVALIDTBRTEMPLATEEXCEPTION                     -> InvalidTBRTemplateException::class.java
            PAUSEMODENOTALLOWEDEXCEPTION                    -> PauseModeNotAllowedException::class.java
            RUNMODENOTALLOWEDEXCEPTION                      -> RunModeNotAllowedException::class.java
            NOACTIVETBRTOCANCELEXCEPTION                    -> NoActiveTBRToCancelException::class.java
            BOLUSTYPEANDPARAMETERMISMATCHEXCEPTION          -> BolusTypeAndParameterMismatchException::class.java
            INVALIDDURATIONPRESETEXCEPTION                  -> InvalidDurationPresetException::class.java
            BOLUSLAGTIMEFEATUREDISABLEDEXCEPTION            -> BolusLagTimeFeatureDisabledException::class.java
            BOLUSDURATIONNOTINRANGEEXCEPTION                -> BolusDurationNotInRangeException::class.java
            INVALIDVALUESOFTWOCHANNELTRANSMISSION           -> InvalidValuesOfTwoChannelTransmission::class.java
            NOSUCHBOLUSTOCANCELEXCEPTION                    -> NoSuchBolusToCancelException::class.java
            MAXIMUMNUMBEROFBOLUSTYPEALREADYRUNNINGEXCEPTION -> MaximumNumberOfBolusTypeAlreadyRunningException::class.java
            CUSTOMBOLUSNOTCONFIGUREDEXCEPTION               -> CustomBolusNotConfiguredException::class.java
            INVALIDDATEPARAMETEREXCEPTION                   -> InvalidDateParameterException::class.java
            INVALIDTIMEPARAMETEREXCEPTION                   -> InvalidTimeParameterException::class.java
            NOCONFIGBLOCKDATAEXCEPTION                      -> NoConfigBlockDataException::class.java
            INVALIDCONFIGBLOCKIDEXCEPTION                   -> InvalidConfigBlockIdException::class.java
            INVALIDCONFIGBLOCKCRCEXCEPTION                  -> InvalidConfigBlockCRCException::class.java
            INVALIDCONFIGBLOCKLENGTHEXCEPTION               -> InvalidConfigBlockLengthException::class.java
            WRITESESSIONALREADYOPENEXCEPTION                -> WriteSessionAlreadyOpenException::class.java
            WRITESESSIONCLOSEDEXCEPTION                     -> WriteSessionClosedException::class.java
            CONFIGMEMORYACCESSEXCEPTION                     -> ConfigMemoryAccessException::class.java
            READINGHISTORYALREADYSTARTEDEXCEPTION           -> ReadingHistoryAlreadyStartedException::class.java
            READINGHISTORYNOTSTARTEDEXCEPTION               -> ReadingHistoryNotStartedException::class.java
            INVALIDPAYLOADEXCEPTION                         -> InvalidPayloadException::class.java
            IMPLAUSIBLEPORTIONLENGTHVALUEEXCEPTION          -> ImplausiblePortionLengthValueException::class.java
            NOTALLOWEDTOACCESSPOSITIONZEROEXCEPTION         -> NotAllowedToAccessPositionZeroException::class.java
            POSITIONPROTECTEDEXCEPTION                      -> PositionProtectedException::class.java
            INVALIDLAGTIMEEXCEPTION                         -> InvalidLagTimeException::class.java
            NOACTIVETBRTOCHANGEEXCEPTION                    -> NoActiveTBRToChangeException::class.java
            else                                            -> null
        }

        private const val PUMPSTOPPEDEXCEPTION = 3178
        private const val BOLUSAMOUNTNOTINRANGEEXCEPTION = 6017
        private const val PUMPALREADYINTHATSTATEEXCEPTION = 3324
        private const val INVALIDSERVICEPASSWORDEXCEPTION = 61593
        private const val UNKNOWNCOMMANDEXCEPTION = 61455
        private const val ALREADYCONNECTEDEXCEPTION = 61530
        private const val WRONGSTATEEXCEPTION = 61680
        private const val SERVICEINCOMPATIBLEEXCEPTION = 61542
        private const val UNKNOWNSERVICEEXCEPTION = 61545
        private const val NOSERVICEPASSWORDNEEDEDEXCEPTION = 61695
        private const val SERVICEALREADYACTIVATEDEXCEPTION = 61644
        private const val INCOMPATIBLEVERSIONEXCEPTION = 61491
        private const val INVALIDPAYLOADLENGTHEXCEPTION = 61500
        private const val NOTCONNECTEDEXCEPTION = 61525
        private const val SERVICECOMMANDNOTAVAILABLEEXCEPTION = 61605
        private const val SERVICENOTACTIVATEDEXCEPTION = 61610
        private const val PUMPBUSYEXCEPTION = 61635
        private const val NOTREFERENCEDEXCEPTION = 5335
        private const val STEPCOUNTOUTOFRANGEEXCEPTION = 5348
        private const val INVALIDPAYLOADCRCEXCEPTION = 2805
        private const val INVALIDPARAMETERTYPEEXCEPTION = 2810
        private const val COMMANDEXECUTIONFAILEDEXCEPTION = 22796
        private const val INVALIDALERTINSTANCEIDEXCEPTION = 3238
        private const val INVALIDTBRFACTOREXCEPTION = 3241
        private const val INVALIDTBRDURATIONEXCEPTION = 3264
        private const val INVALIDTBRTEMPLATEEXCEPTION = 6363
        private const val PAUSEMODENOTALLOWEDEXCEPTION = 3315
        private const val RUNMODENOTALLOWEDEXCEPTION = 3279
        private const val NOACTIVETBRTOCANCELEXCEPTION = 3840
        private const val BOLUSTYPEANDPARAMETERMISMATCHEXCEPTION = 3925
        private const val INVALIDDURATIONPRESETEXCEPTION = 5924
        private const val BOLUSLAGTIMEFEATUREDISABLEDEXCEPTION = 90
        private const val BOLUSDURATIONNOTINRANGEEXCEPTION = 6014
        private const val INVALIDVALUESOFTWOCHANNELTRANSMISSION = 0x0F96
        private const val NOSUCHBOLUSTOCANCELEXCEPTION = 4005
        private const val MAXIMUMNUMBEROFBOLUSTYPEALREADYRUNNINGEXCEPTION = 4010
        private const val CUSTOMBOLUSNOTCONFIGUREDEXCEPTION = 6270
        private const val INVALIDDATEPARAMETEREXCEPTION = 4044
        private const val INVALIDTIMEPARAMETEREXCEPTION = 4080
        private const val NOCONFIGBLOCKDATAEXCEPTION = 4471
        private const val INVALIDCONFIGBLOCKIDEXCEPTION = 4472
        private const val INVALIDCONFIGBLOCKCRCEXCEPTION = 4487
        private const val INVALIDCONFIGBLOCKLENGTHEXCEPTION = 6286
        private const val WRITESESSIONALREADYOPENEXCEPTION = 4539
        private const val WRITESESSIONCLOSEDEXCEPTION = 4562
        private const val CONFIGMEMORYACCESSEXCEPTION = 4573
        private const val READINGHISTORYALREADYSTARTEDEXCEPTION = 11794
        private const val READINGHISTORYNOTSTARTEDEXCEPTION = 4680
        private const val INVALIDPAYLOADEXCEPTION = 6210
        private const val IMPLAUSIBLEPORTIONLENGTHVALUEEXCEPTION = 4824
        private const val NOTALLOWEDTOACCESSPOSITIONZEROEXCEPTION = 4830
        private const val POSITIONPROTECTEDEXCEPTION = 4845
        private const val INVALIDLAGTIMEEXCEPTION = 3891
        private const val NOACTIVETBRTOCHANGEEXCEPTION = 6322

    }
}