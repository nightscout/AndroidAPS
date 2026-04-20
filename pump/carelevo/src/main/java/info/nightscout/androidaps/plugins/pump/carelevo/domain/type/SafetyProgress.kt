package info.nightscout.androidaps.plugins.pump.carelevo.domain.type

import info.nightscout.androidaps.plugins.pump.carelevo.domain.model.bt.SafetyCheckResultModel

sealed class SafetyProgress {
    data class Progress(val timeoutSec: Long) : SafetyProgress()
    data class Success(val result: SafetyCheckResultModel) : SafetyProgress()
    data class Error(val throwable: Throwable) : SafetyProgress()
}