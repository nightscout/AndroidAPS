package app.aaps.pump.omnipod.common.bledriver.pod.command.base

abstract class NonceEnabledCommand protected constructor(
    commandType: CommandType,
    uniqueId: Int,
    sequenceNumber: Short,
    multiCommandFlag: Boolean,
    protected val nonce: Int
) : HeaderEnabledCommand(commandType, uniqueId, sequenceNumber, multiCommandFlag)
