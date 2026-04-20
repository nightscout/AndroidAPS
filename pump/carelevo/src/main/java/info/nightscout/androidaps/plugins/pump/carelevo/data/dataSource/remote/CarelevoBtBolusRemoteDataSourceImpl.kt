package info.nightscout.androidaps.plugins.pump.carelevo.data.dataSource.remote

import info.nightscout.androidaps.plugins.pump.carelevo.ble.CarelevoBleSource
import info.nightscout.androidaps.plugins.pump.carelevo.ble.core.CarelevoBleController
import info.nightscout.androidaps.plugins.pump.carelevo.ble.data.CommandResult
import info.nightscout.androidaps.plugins.pump.carelevo.data.common.buildWriteCommand
import info.nightscout.androidaps.plugins.pump.carelevo.data.common.createMessage
import info.nightscout.androidaps.plugins.pump.carelevo.data.common.handleBtResponse
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.BleResponse
import info.nightscout.androidaps.plugins.pump.carelevo.data.model.ble.ProtocolRspModel
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.command.CarelevoProtocolCommand
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.command.CarelevoProtocolCommand.Companion.commandToCode
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.parser.CarelevoProtocolParserProvider
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer.CarelevoDoubleToByteTransformerImpl
import info.nightscout.androidaps.plugins.pump.carelevo.data.protocol.transformer.CarelevoIntegerToByteTransformerImpl
import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.isBolusProtocol
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class CarelevoBtBolusRemoteDataSourceImpl @Inject constructor(
    private val bleController : CarelevoBleController,
    private val provider : CarelevoProtocolParserProvider
) : CarelevoBtBolusRemoteDataSource {

    private val _bolusResponse = CarelevoBleSource.notifyIndicateByte
        .flatMap { charResult ->
            charResult.value?.run {
                val command = first()
                if(isBolusProtocol(command.toUByte().toInt())) {
                    val res = runCatching {
                        handleBtResponse<ByteArray, ProtocolRspModel> {
                            val model = provider.getModel(command.toUByte().toInt())
                            (model?.let {
                                val parser = provider.getParser(it)
                                parser?.parse(this)
                            } ?: throw IllegalStateException()) as ProtocolRspModel
                        }
                    }.fold(
                        onSuccess = {
                            it
                        },
                        onFailure = {
                            BleResponse.Error(it)
                        }
                    )
                    Observable.just(res)
                } else {
                    Observable.just(BleResponse.Failure(""))
                }
            } ?: Observable.just(BleResponse.Failure(""))
        }

    override fun getBolusResponse(): Observable<BleResponse<ProtocolRspModel>> {
        return _bolusResponse
    }

    override fun manipulateStartImmeBolusInfusionProgram(actionId: Int, bolus: Double): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_IMMED_BOLUS_REQ.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(0, 255).transform(actionId),
                CarelevoDoubleToByteTransformerImpl(0.0, 0.0).transform(bolus)
            ).run {
                buildWriteCommand(bleController, this)
            }.run {
                bleController.pend(this)
            }
        }.fold(
            onSuccess = {
                Single.just(it)
            },
            onFailure = {
                Single.just(CommandResult.Error(it))
            }
        )
    }

    override fun manipulateCancelImmeBolusInfusionProgram(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BOLUS_CANCEL_REQ.commandToCode())
            ).run {
                buildWriteCommand(bleController, this)
            }.run {
                bleController.pend(this)
            }
        }.fold(
            onSuccess = {
                Single.just(it)
            },
            onFailure = {
                Single.just(CommandResult.Error(it))
            }
        )
    }

    override fun manipulateStartExtendBolusInfusionProgram(immeDose: Double, extendSpeed: Double, hour: Int, min: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_EXTENDED_BOLUS_REQ.commandToCode()),
                CarelevoDoubleToByteTransformerImpl(0.0, 0.0).transform(immeDose),
                CarelevoDoubleToByteTransformerImpl(0.0, 0.0).transform(extendSpeed),
                CarelevoIntegerToByteTransformerImpl(0, 24).transform(hour),
                CarelevoIntegerToByteTransformerImpl(0, 59).transform(min)
            ).run {
                buildWriteCommand(bleController, this)
            }.run {
                bleController.pend(this)
            }
        }.fold(
            onSuccess = {
                Single.just(it)
            },
            onFailure = {
                Single.just(CommandResult.Error(it))
            }
        )
    }

    override fun manipulateCancelExtendBolusInfusionProgram(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_EXTEND_BOLUS_CANCEL_REQ.commandToCode())
            ).run {
                buildWriteCommand(bleController, this)
            }.run {
                bleController.pend(this)
            }
        }.fold(
            onSuccess = {
                Single.just(it)
            },
            onFailure = {
                Single.just(CommandResult.Error(it))
            }
        )
    }
}