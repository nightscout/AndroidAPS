package app.aaps.pump.eopatch.core.response

class UpdateConnectionResponse(stateBytes: ByteArray) : BaseResponse() {

    val patchState: ByteArray = ByteArray(20)

    init {
        System.arraycopy(stateBytes, 0, patchState, 0, 20)
    }
}
