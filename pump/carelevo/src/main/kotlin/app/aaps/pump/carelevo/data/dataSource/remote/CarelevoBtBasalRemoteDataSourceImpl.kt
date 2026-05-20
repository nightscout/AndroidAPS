package app.aaps.pump.carelevo.data.dataSource.remote

import app.aaps.pump.carelevo.ble.CarelevoBleSource
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.ble.data.CommandResult
import app.aaps.pump.carelevo.data.common.buildWriteCommand
import app.aaps.pump.carelevo.data.common.createMessage
import app.aaps.pump.carelevo.data.common.handleBtResponse
import app.aaps.pump.carelevo.data.model.ble.BleResponse
import app.aaps.pump.carelevo.data.model.ble.ProtocolRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSegmentModel
import app.aaps.pump.carelevo.data.protocol.command.CarelevoProtocolCommand
import app.aaps.pump.carelevo.data.protocol.command.CarelevoProtocolCommand.Companion.commandToCode
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParserProvider
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoBasalProgramToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoBasalProgramToByteTransformerV2Impl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoBooleanToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoDoubleToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoIntegerToByteTransformerImpl
import app.aaps.pump.carelevo.domain.model.bt.isBasalProtocol
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class CarelevoBtBasalRemoteDataSourceImpl @Inject constructor(
    private val bleController: CarelevoBleController,
    private val provider: CarelevoProtocolParserProvider
) : CarelevoBtBasalRemoteDataSource {

    private val _basalResponse = CarelevoBleSource.notifyIndicateByte
        .flatMap { charResult ->
            charResult.value?.run {
                val command = first()
                if (isBasalProtocol(command.toUByte().toInt())) {
                    val res = runCatching {
                        handleBtResponse<ByteArray, ProtocolRspModel> {
                            val model = provider.getModel(command.toUByte().toInt())
                            (model?.let {
                                val parser = provider.getParser(model)
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

    override fun getBasalResponse(): Observable<BleResponse<ProtocolRspModel>> {
        return _basalResponse
    }

    override fun setBasalInfusionProgram(totalSegmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BASAL_PROGRAM_REQ1.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(1, 24).transform(totalSegmentCnt),
                CarelevoBasalProgramToByteTransformerImpl().transform(segments)
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

    override fun setAdditionalBasalInfusionProgram(msgNo: Int, segmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BASAL_PROGRAM_REQ2.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(2, 6).transform(msgNo),
                CarelevoIntegerToByteTransformerImpl(1, 4).transform(segmentCnt),
                CarelevoBasalProgramToByteTransformerImpl().transform(segments)
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

    override fun setUpdateBasalInfusionProgram(totalSegmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BASAL_CHANGE_REQ1.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(1, 24).transform(totalSegmentCnt),
                CarelevoBasalProgramToByteTransformerImpl().transform(segments)
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

    override fun setUpdateAdditionalBasalInfusionProgram(msgNo: Int, segmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BASAL_CHANGE_REQ2.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(2, 6).transform(msgNo),
                CarelevoIntegerToByteTransformerImpl(1, 4).transform(segmentCnt),
                CarelevoBasalProgramToByteTransformerImpl().transform(segments)
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

    override fun manipulateStartTempBasalInfusionProgramByUnit(infusionUnit: Double, infusionHour: Int, infusionMin: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_TEMP_BASAL_REQ.commandToCode()),
                CarelevoDoubleToByteTransformerImpl(0.0, 0.0).transform(infusionUnit),
                CarelevoIntegerToByteTransformerImpl(0, 24).transform(infusionHour),
                CarelevoIntegerToByteTransformerImpl(0, 59).transform(infusionMin),
                CarelevoBooleanToByteTransformerImpl().transform(true)
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

    override fun manipulateStartTempBasalInfusionProgramByPercent(infusionPercent: Int, infusionHour: Int, infusionMin: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_TEMP_BASAL_REQ.commandToCode()),
                CarelevoDoubleToByteTransformerImpl(0.0, 200.0).transform(infusionPercent.toDouble() / 100.0),
                CarelevoIntegerToByteTransformerImpl(0, 24).transform(infusionHour),
                CarelevoIntegerToByteTransformerImpl(0, 59).transform(infusionMin)
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

    override fun manipulateCancelTempBasalInfusionProgram(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_TEMP_BASAL_CANCEL_REQ.commandToCode())
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

    override fun setBasalInfusionProgramV2(seqNo: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BASAL_PROGRAM_REQ1.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(0, 2).transform(seqNo),
                CarelevoBasalProgramToByteTransformerV2Impl().transform(segments)
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

    override fun updateBasalInfusionProgramV2(seqNo: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BASAL_CHANGE_REQ1.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(0, 2).transform(seqNo),
                CarelevoBasalProgramToByteTransformerV2Impl().transform(segments)
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