package app.aaps.pump.eopatch.core.response

class BasalHistoryIndexResponse(
    val lastIndex: Int,
    val curIndex: Int
) : BaseResponse() {

    val lastFinishedIndex: Int =
        if (curIndex > 0 && curIndex != 0xFFFF) curIndex - 1 else -1
}
