package info.nightscout.comboctl.utils

fun <T> retryBlocking(
    numberOfRetries: Int,
    delayBetweenRetries: Long = 100,
    block: (Int, Exception?) -> T
): T {
    require(numberOfRetries > 0)

    var previousException: Exception? = null
    repeat(numberOfRetries - 1) { attemptNumber ->
        try {
            return block(attemptNumber, previousException)
        } catch (exception: Exception) {
            previousException = exception
        }
        Thread.sleep(delayBetweenRetries)
    }

    // The last attempt. This one is _not_ surrounded with a try-catch
    // block to make sure that if even the last attempt fails with an
    // exception the caller gets informed about said exception.
    return block(numberOfRetries - 1, previousException)
}
