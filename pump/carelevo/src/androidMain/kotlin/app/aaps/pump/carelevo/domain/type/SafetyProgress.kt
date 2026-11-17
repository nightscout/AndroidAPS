package app.aaps.pump.carelevo.domain.type

import app.aaps.pump.carelevo.domain.model.bt.SafetyCheckResultModel

sealed class SafetyProgress {
    data class Progress(val timeoutSec: Long) : SafetyProgress()
    data class Success(val result: SafetyCheckResultModel) : SafetyProgress()
    data class Error(val throwable: Throwable) : SafetyProgress()
}