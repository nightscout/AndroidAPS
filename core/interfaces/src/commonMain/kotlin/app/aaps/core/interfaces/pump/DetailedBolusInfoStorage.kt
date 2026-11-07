package app.aaps.core.interfaces.pump

interface DetailedBolusInfoStorage {

    fun add(detailedBolusInfo: DetailedBolusInfo)
    fun findDetailedBolusInfo(bolusTime: Long, bolus: Double): DetailedBolusInfo?
}