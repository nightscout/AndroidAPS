package app.aaps.core.ui.compose.pump

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits [Unit] every [periodMs] milliseconds.
 * Used by pump ViewModels as a combine() source to refresh relative time strings
 * ("5 min ago" → "6 min ago").
 */
fun tickerFlow(periodMs: Long = 30_000L): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}
