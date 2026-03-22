package app.aaps.pump.eopatch.core.response

class BasalHistoryResponse(
    val seq: Int,
    val injectedDoseValues: FloatArray,
    private val isTemp: Boolean
) : BaseResponse() {

    override fun toString(): String =
        "BasalHistoryResponse{injectedDoseValues=${injectedDoseValues.contentToString()}, seq=$seq, isTemp=$isTemp}"
}
