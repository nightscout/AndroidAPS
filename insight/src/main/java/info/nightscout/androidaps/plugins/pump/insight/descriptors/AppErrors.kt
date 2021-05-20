package info.nightscout.androidaps.plugins.pump.insight.descriptors

import info.nightscout.androidaps.plugins.pump.insight.exceptions.app_layer_errors.*

enum class AppErrors (val id: Int, val type: Class<out AppLayerErrorException?>)  {
    PUMPSTOPPEDEXCEPTION (3178, PumpStoppedException::class.java),
    BOLUSAMOUNTNOTINRANGEEXCEPTION (6017, BolusAmountNotInRangeException::class.java),
    PUMPALREADYINTHATSTATEEXCEPTION (3324, PumpAlreadyInThatStateException::class.java),
    INVALIDSERVICEPASSWORDEXCEPTION (61593, InvalidServicePasswordException::class.java),
    UNKNOWNCOMMANDEXCEPTION (61455, UnknownCommandException::class.java),
    ALREADYCONNECTEDEXCEPTION (61530, AlreadyConnectedException::class.java),
    WRONGSTATEEXCEPTION (61680, WrongStateException::class.java),
    SERVICEINCOMPATIBLEEXCEPTION (61542, ServiceIncompatibleException::class.java),
    UNKNOWNSERVICEEXCEPTION (61545, UnknownServiceException::class.java),
    NOSERVICEPASSWORDNEEDEDEXCEPTION (61695, NoServicePasswordNeededException::class.java),
    SERVICEALREADYACTIVATEDEXCEPTION (61644, ServiceAlreadyActivatedException::class.java),
    INCOMPATIBLEVERSIONEXCEPTION (61491, IncompatibleVersionException::class.java),
    INVALIDPAYLOADLENGTHEXCEPTION (61500, InvalidPayloadLengthException::class.java),
    NOTCONNECTEDEXCEPTION (61525, NotConnectedException::class.java),
    SERVICECOMMANDNOTAVAILABLEEXCEPTION (61605, ServiceCommandNotAvailableException::class.java),
    SERVICENOTACTIVATEDEXCEPTION (61610, ServiceNotActivatedException::class.java),
    PUMPBUSYEXCEPTION (61635, PumpBusyException::class.java),
    NOTREFERENCEDEXCEPTION (5335, NotReferencedException::class.java),
    STEPCOUNTOUTOFRANGEEXCEPTION (5348, StepCountOutOfRangeException::class.java),
    INVALIDPAYLOADCRCEXCEPTION (2805, InvalidPayloadCRCException::class.java),
    INVALIDPARAMETERTYPEEXCEPTION (2810, InvalidParameterTypeException::class.java),
    COMMANDEXECUTIONFAILEDEXCEPTION (22796, CommandExecutionFailedException::class.java),
    INVALIDALERTINSTANCEIDEXCEPTION (3238, InvalidAlertInstanceIdException::class.java),
    INVALIDTBRFACTOREXCEPTION (3241, InvalidTBRFactorException::class.java),
    INVALIDTBRDURATIONEXCEPTION (3264, InvalidTBRDurationException::class.java),
    INVALIDTBRTEMPLATEEXCEPTION (6363, InvalidTBRTemplateException::class.java),
    PAUSEMODENOTALLOWEDEXCEPTION (3315, PauseModeNotAllowedException::class.java),
    RUNMODENOTALLOWEDEXCEPTION (3279, RunModeNotAllowedException::class.java),
    NOACTIVETBRTOCANCELEXCEPTION (3840, NoActiveTBRToCanceLException::class.java),
    BOLUSTYPEANDPARAMETERMISMATCHEXCEPTION (3925, BolusTypeAndParameterMismatchException::class.java),
    INVALIDDURATIONPRESETEXCEPTION (5924, InvalidDurationPresetException::class.java),
    BOLUSLAGTIMEFEATUREDISABLEDEXCEPTION (90, BolusLagTimeFeatureDisabledException::class.java),
    BOLUSDURATIONNOTINRANGEEXCEPTION (6014, BolusDurationNotInRangeException::class.java),
    INVALIDVALUESOFTWOCHANNELTRANSMISSION (0x0F96, InvalidValuesOfTwoChannelTransmission::class.java),
    NOSUCHBOLUSTOCANCELEXCEPTION (4005, NoSuchBolusToCancelException::class.java),
    MAXIMUMNUMBEROFBOLUSTYPEALREADYRUNNINGEXCEPTION (4010, MaximumNumberOfBolusTypeAlreadyRunningException::class.java),
    CUSTOMBOLUSNOTCONFIGUREDEXCEPTION (6270, CustomBolusNotConfiguredException::class.java),
    INVALIDDATEPARAMETEREXCEPTION (4044, InvalidDateParameterException::class.java),
    INVALIDTIMEPARAMETEREXCEPTION (4080, InvalidTimeParameterException::class.java),
    NOCONFIGBLOCKDATAEXCEPTION (4471, NoConfigBlockDataException::class.java),
    INVALIDCONFIGBLOCKIDEXCEPTION (4472, InvalidConfigBlockIdException::class.java),
    INVALIDCONFIGBLOCKCRCEXCEPTION (4487, InvalidConfigBlockCRCException::class.java),
    INVALIDCONFIGBLOCKLENGTHEXCEPTION (6286, InvalidConfigBlockLengthException::class.java),
    WRITESESSIONALREADYOPENEXCEPTION (4539, WriteSessionAlreadyOpenException::class.java),
    WRITESESSIONCLOSEDEXCEPTION (4562, WriteSessionClosedException::class.java),
    CONFIGMEMORYACCESSEXCEPTION (4573, ConfigMemoryAccessException::class.java),
    READINGHISTORYALREADYSTARTEDEXCEPTION (11794, ReadingHistoryAlreadyStartedException::class.java),
    READINGHISTORYNOTSTARTEDEXCEPTION (4680, ReadingHistoryNotStartedException::class.java),
    INVALIDPAYLOADEXCEPTION (6210, InvalidPayloadException::class.java),
    IMPLAUSIBLEPORTIONLENGTHVALUEEXCEPTION (4824, ImplausiblePortionLengthValueException::class.java),
    NOTALLOWEDTOACCESSPOSITIONZEROEXCEPTION (4830, NotAllowedToAccessPositionZeroException::class.java),
    POSITIONPROTECTEDEXCEPTION (4845, PositionProtectedException::class.java),
    INVALIDLAGTIMEEXCEPTION (3891, InvalidLagTimeException::class.java),
    NOACTIVETBRTOCHANGEEXCEPTION (6322, NoActiveTBRToChangeException::class.java);


    companion object {
        fun fromType(type: Class<out AppLayerErrorException?>) = values().firstOrNull { it.type == type }
        fun fromId(id: Int) = values().firstOrNull { it.id == id }

    }
}