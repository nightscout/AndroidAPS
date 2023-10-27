package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.*

class AppErrors (val id: Int, val type: Class<out AppLayerErrorException?>)  {

    companion object {
        fun fromId(id: Int) : Class<out AppLayerErrorException?>? = when(id) {
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
            NOACTIVETBRTOCANCELEXCEPTION                    -> NoActiveTBRToCanceLException::class.java
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

        const val PUMPSTOPPEDEXCEPTION = 3178
        const val BOLUSAMOUNTNOTINRANGEEXCEPTION = 6017
        const val PUMPALREADYINTHATSTATEEXCEPTION = 3324
        const val INVALIDSERVICEPASSWORDEXCEPTION = 61593
        const val UNKNOWNCOMMANDEXCEPTION = 61455
        const val ALREADYCONNECTEDEXCEPTION = 61530
        const val WRONGSTATEEXCEPTION = 61680
        const val SERVICEINCOMPATIBLEEXCEPTION = 61542
        const val UNKNOWNSERVICEEXCEPTION = 61545
        const val NOSERVICEPASSWORDNEEDEDEXCEPTION = 61695
        const val SERVICEALREADYACTIVATEDEXCEPTION = 61644
        const val INCOMPATIBLEVERSIONEXCEPTION = 61491
        const val INVALIDPAYLOADLENGTHEXCEPTION = 61500
        const val NOTCONNECTEDEXCEPTION = 61525
        const val SERVICECOMMANDNOTAVAILABLEEXCEPTION = 61605
        const val SERVICENOTACTIVATEDEXCEPTION = 61610
        const val PUMPBUSYEXCEPTION = 61635
        const val NOTREFERENCEDEXCEPTION = 5335
        const val STEPCOUNTOUTOFRANGEEXCEPTION = 5348
        const val INVALIDPAYLOADCRCEXCEPTION = 2805
        const val INVALIDPARAMETERTYPEEXCEPTION = 2810
        const val COMMANDEXECUTIONFAILEDEXCEPTION = 22796
        const val INVALIDALERTINSTANCEIDEXCEPTION = 3238
        const val INVALIDTBRFACTOREXCEPTION = 3241
        const val INVALIDTBRDURATIONEXCEPTION = 3264
        const val INVALIDTBRTEMPLATEEXCEPTION = 6363
        const val PAUSEMODENOTALLOWEDEXCEPTION = 3315
        const val RUNMODENOTALLOWEDEXCEPTION = 3279
        const val NOACTIVETBRTOCANCELEXCEPTION = 3840
        const val BOLUSTYPEANDPARAMETERMISMATCHEXCEPTION = 3925
        const val INVALIDDURATIONPRESETEXCEPTION = 5924
        const val BOLUSLAGTIMEFEATUREDISABLEDEXCEPTION = 90
        const val BOLUSDURATIONNOTINRANGEEXCEPTION = 6014
        const val INVALIDVALUESOFTWOCHANNELTRANSMISSION = 0x0F96
        const val NOSUCHBOLUSTOCANCELEXCEPTION = 4005
        const val MAXIMUMNUMBEROFBOLUSTYPEALREADYRUNNINGEXCEPTION = 4010
        const val CUSTOMBOLUSNOTCONFIGUREDEXCEPTION = 6270
        const val INVALIDDATEPARAMETEREXCEPTION = 4044
        const val INVALIDTIMEPARAMETEREXCEPTION = 4080
        const val NOCONFIGBLOCKDATAEXCEPTION = 4471
        const val INVALIDCONFIGBLOCKIDEXCEPTION = 4472
        const val INVALIDCONFIGBLOCKCRCEXCEPTION = 4487
        const val INVALIDCONFIGBLOCKLENGTHEXCEPTION = 6286
        const val WRITESESSIONALREADYOPENEXCEPTION = 4539
        const val WRITESESSIONCLOSEDEXCEPTION = 4562
        const val CONFIGMEMORYACCESSEXCEPTION = 4573
        const val READINGHISTORYALREADYSTARTEDEXCEPTION = 11794
        const val READINGHISTORYNOTSTARTEDEXCEPTION = 4680
        const val INVALIDPAYLOADEXCEPTION = 6210
        const val IMPLAUSIBLEPORTIONLENGTHVALUEEXCEPTION = 4824
        const val NOTALLOWEDTOACCESSPOSITIONZEROEXCEPTION = 4830
        const val POSITIONPROTECTEDEXCEPTION = 4845
        const val INVALIDLAGTIMEEXCEPTION = 3891
        const val NOACTIVETBRTOCHANGEEXCEPTION = 6322

    }
}