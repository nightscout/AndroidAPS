package app.aaps.pump.carelevo.data.dataSource.remote

import app.aaps.pump.carelevo.ble.CarelevoBleSource
import app.aaps.pump.carelevo.ble.core.CarelevoBleController
import app.aaps.pump.carelevo.ble.data.CommandResult
import app.aaps.pump.carelevo.data.common.buildWriteCommand
import app.aaps.pump.carelevo.data.common.createMessage
import app.aaps.pump.carelevo.data.common.handleBtResponse
import app.aaps.pump.carelevo.data.model.ble.BleResponse
import app.aaps.pump.carelevo.data.model.ble.ProtocolRspModel
import app.aaps.pump.carelevo.data.protocol.command.CarelevoProtocolCommand
import app.aaps.pump.carelevo.data.protocol.command.CarelevoProtocolCommand.Companion.commandToCode
import app.aaps.pump.carelevo.data.protocol.parser.CarelevoProtocolParserProvider
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoBooleanToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoDateTimeToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoDoubleToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoIntegerDivideUnitToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoIntegerToByteTransformerImpl
import app.aaps.pump.carelevo.data.protocol.transformer.CarelevoSubIdToByteTransformerImpl
import app.aaps.pump.carelevo.domain.model.bt.isPatchProtocol
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import javax.inject.Inject

class CarelevoBtPatchRemoteDataSourceImpl @Inject constructor(
    private val bleController: CarelevoBleController,
    private val provider: CarelevoProtocolParserProvider
) : CarelevoBtPatchRemoteDataSource {

    private val _patchResponse = CarelevoBleSource.notifyIndicateByte
        .flatMap { charResult ->
            charResult.value?.run {
                val command = first()
                if (isPatchProtocol(command.toUByte().toInt())) {
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

    override fun getPatchResponse(): Observable<BleResponse<ProtocolRspModel>> {
        return _patchResponse
    }

    override fun setTime(dateTime: String, volume: Int, subId: Int, aidMode: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_SET_TIME_REQ.commandToCode()),
                byteArrayOf(subId.toByte()),
                CarelevoDateTimeToByteTransformerImpl().transform(dateTime),
                CarelevoIntegerDivideUnitToByteTransformerImpl(0, 300).transform(volume),
                byteArrayOf(aidMode.toByte())
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

    override fun manipulateSafetyCheckStart(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_SAFETY_CHECK_REQ.commandToCode())
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

    override fun setExpiryExtend(extendHour: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_USAGE_TIME_EXTEND_REQ.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(1, 12).transform(extendHour)
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

    override fun setAppAuth(key: Byte): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                // byteArrayOf(CarelevoProtocolCommand.CMD_APP_AUTH_KEY_IND.commandToCode()),
                byteArrayOf(CarelevoProtocolCommand.CMD_APP_AUTH_IND.commandToCode()),
                byteArrayOf(key)
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

    override fun setAppAuthAck(isSuccess: Boolean): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_APP_AUTH_RPT.commandToCode()),
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

    override fun setThresholdNotice(value: Int, type: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            if (type == 1) {
                createMessage(
                    byteArrayOf(CarelevoProtocolCommand.CMD_NOTICE_THRESHOLD_REQ.commandToCode()),
                    CarelevoIntegerToByteTransformerImpl(0, 0).transform(type),
                    CarelevoIntegerToByteTransformerImpl(24, 167).transform(value)
                )
            } else {
                createMessage(
                    byteArrayOf(CarelevoProtocolCommand.CMD_NOTICE_THRESHOLD_REQ.commandToCode()),
                    CarelevoIntegerToByteTransformerImpl(0, 0).transform(type),
                    CarelevoIntegerToByteTransformerImpl(20, 50).transform(value)
                )
            }.run {
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

    override fun setThresholdInsulinMaxSpeed(value: Double): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_INFUSION_THRESHOLD_REQ.commandToCode()),
                CarelevoBooleanToByteTransformerImpl().transform(true),
                CarelevoDoubleToByteTransformerImpl(0.05, 15.0).transform(value)
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

    override fun setThresholdInsulinMaxVolume(value: Double): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_INFUSION_THRESHOLD_REQ.commandToCode()),
                CarelevoBooleanToByteTransformerImpl().transform(false),
                CarelevoDoubleToByteTransformerImpl(0.05, 25.0).transform(value)
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

    override fun setThreshold(insulinRemainsThreshold: Int, expiryThreshold: Int, maxBasalSpeed: Double, maxBolusDose: Double, buzzUse: Boolean): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_THRESHOLD_SETUP_REQ.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(10, 300).transform(insulinRemainsThreshold),
                CarelevoIntegerToByteTransformerImpl(24, 167).transform(expiryThreshold),
                CarelevoDoubleToByteTransformerImpl(0.05, 15.0).transform(maxBasalSpeed),
                CarelevoDoubleToByteTransformerImpl(0.05, 25.0).transform(maxBolusDose),
                CarelevoBooleanToByteTransformerImpl().transform(!buzzUse)
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

    override fun setBuzzMode(use: Boolean): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BUZZ_CHANGE_REQ.commandToCode()),
                CarelevoBooleanToByteTransformerImpl().transform(!use)
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

    override fun setInfusionThreshold(isBasal: Boolean, threshold: Double): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_INFUSION_THRESHOLD_REQ.commandToCode()),
                CarelevoBooleanToByteTransformerImpl().transform(isBasal),
                CarelevoDoubleToByteTransformerImpl(0.0, 0.0).transform(threshold)
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

    override fun setThresholdSet(): Single<CommandResult<Boolean>> {
        return runCatching {
            CommandResult.Success(false)
        }.fold(
            onSuccess = {
                Single.just(it)
            },
            onFailure = {
                Single.just(CommandResult.Error(it))
            }
        )
    }

    override fun setApplicationStatus(isAppBackground: Boolean, hour: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_APP_STATUS_IND.commandToCode()),
                CarelevoBooleanToByteTransformerImpl().transform(isAppBackground),
                CarelevoIntegerToByteTransformerImpl(0, 24).transform(hour)
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

    override fun setAlarmMode(mode: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_ALERT_ALARM_SET_REQ.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(0, 0).transform(mode)
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

    override fun retrieveCannulaStatus(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_NEEDLE_STATUS_REQ.commandToCode()),
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

    override fun retrieveInfusionStatusInformation(inquiryType: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_INFUSION_INFO_REQ.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(0, 1).transform(inquiryType)
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

    override fun retrievePatchDeviceInformation(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PATCH_INFO_REQ.commandToCode())
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

    override fun retrievePatchOperationInformation(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PATCH_OPERATIONAL_DATA_REQ.commandToCode())
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

    override fun retrieveThresholds(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_THRESHOLD_VALUE_REQ.commandToCode())
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

    override fun retrieveMacAddress(key: Byte): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_MAC_ADDR_REQ.commandToCode()),
                byteArrayOf(key)
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

    override fun manipulatePumpStop(min: Int, subId: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            val hour = min / 60
            val minute = min % 60
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PUMP_STOP_REQ.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(0, 5).transform(hour),
                CarelevoIntegerToByteTransformerImpl(0, 60).transform(minute),
                CarelevoIntegerToByteTransformerImpl(0, 1).transform(subId)
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

    override fun manipulatePumpResume(mode: Int, subId: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PUMP_RESTART_REQ.commandToCode()),
                CarelevoIntegerToByteTransformerImpl(1, 4).transform(mode),
                CarelevoSubIdToByteTransformerImpl().transform(subId)
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

    override fun manipulatePumpStopAck(subId: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PUMP_STOP_ACK.commandToCode()),
                CarelevoSubIdToByteTransformerImpl().transform(subId)
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

    override fun manipulateDiscardPatch(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PATCH_DISCARD_REQ.commandToCode())
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

    override fun manipulateBuzzRunning(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_BUZZER_CHECK_REQ.commandToCode())
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

    override fun manipulateInitialize(mode: Boolean): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PATCH_INIT_REQ.commandToCode()),
                CarelevoBooleanToByteTransformerImpl().transform(mode)
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

    override fun manipulateClearAlarm(alarmType: Int, cause: Int): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_ALARM_CLEAR_REQ.commandToCode()),
                byteArrayOf(alarmType.toByte()),
                CarelevoIntegerToByteTransformerImpl(0, 100).transform(cause)
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

    override fun manipulateAdditionalPriming(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_ADD_PRIMING_REQ.commandToCode())
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

    override fun confirmReportCannulaInsertion(isSuccess: Boolean): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_NEEDLE_INSERT_ACK.commandToCode()),
                CarelevoBooleanToByteTransformerImpl().transform(isSuccess)
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

    override fun confirmPatchRecovery(): Single<CommandResult<Boolean>> {
        return runCatching {
            createMessage(
                byteArrayOf(CarelevoProtocolCommand.CMD_PATCH_RECOVERY_RPT.commandToCode())
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
