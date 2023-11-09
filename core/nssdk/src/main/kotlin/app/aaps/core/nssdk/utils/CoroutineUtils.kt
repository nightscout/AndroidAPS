package app.aaps.core.nssdk.utils

import kotlinx.coroutines.delay
import kotlin.reflect.KClass

@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> retry(
    numberOfRetries: Int,
    delayBetweenRetries: Long,
    excludedExceptions: List<KClass<out java.lang.Exception>>,
    block: suspend () -> T
): T {
    repeat(numberOfRetries) {
        try {
            return block()
        } catch (exception: Exception) {
            if (exception::class in excludedExceptions) throw exception
        }
        delay(delayBetweenRetries)
    }
    return block()
}
