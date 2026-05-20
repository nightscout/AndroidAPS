package app.aaps.pump.carelevo.data.dataSource.remote

import app.aaps.pump.carelevo.ble.data.CommandResult
import app.aaps.pump.carelevo.data.model.ble.BleResponse
import app.aaps.pump.carelevo.data.model.ble.ProtocolRspModel
import app.aaps.pump.carelevo.data.model.ble.ProtocolSegmentModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single

interface CarelevoBtBasalRemoteDataSource {

    fun getBasalResponse(): Observable<BleResponse<ProtocolRspModel>>

    fun setBasalInfusionProgram(totalSegmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>>
    fun setAdditionalBasalInfusionProgram(msgNo: Int, segmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>>

    fun setUpdateBasalInfusionProgram(totalSegmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>>
    fun setUpdateAdditionalBasalInfusionProgram(msgNo: Int, segmentCnt: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>>

    fun manipulateStartTempBasalInfusionProgramByUnit(infusionUnit: Double, infusionHour: Int, infusionMin: Int): Single<CommandResult<Boolean>>
    fun manipulateStartTempBasalInfusionProgramByPercent(infusionPercent: Int, infusionHour: Int, infusionMin: Int): Single<CommandResult<Boolean>>

    fun manipulateCancelTempBasalInfusionProgram(): Single<CommandResult<Boolean>>

    fun setBasalInfusionProgramV2(seqNo: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>>
    fun updateBasalInfusionProgramV2(seqNo: Int, segments: List<ProtocolSegmentModel>): Single<CommandResult<Boolean>>
}