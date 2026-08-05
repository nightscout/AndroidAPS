package app.aaps.core.data.aps

import app.aaps.core.data.model.TDD

data class AverageTDD (
    var data: TDD,
    val allDaysHaveCarbs: Boolean
)