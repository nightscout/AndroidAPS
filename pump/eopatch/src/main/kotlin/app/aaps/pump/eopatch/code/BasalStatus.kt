package app.aaps.pump.eopatch.code

enum class BasalStatus(val rawValue: Int) {
    STOPPED(0),
    PAUSED(1),     //템프베이젤 주입중
    SUSPENDED(2),  //주입 정지
    STARTED(3),    //주입중
    SELECTED(4);   //패치 폐기

    val isStarted: Boolean
        get() = this == STARTED

    val isSuspended: Boolean
        get() = this == SUSPENDED

    val isStopped: Boolean
        get() = this == STOPPED
}
