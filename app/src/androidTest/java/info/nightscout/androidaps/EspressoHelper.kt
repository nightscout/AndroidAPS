package info.nightscout.androidaps

import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers

fun ViewInteraction.isDisplayed(): Boolean {
    try {
        check(matches(ViewMatchers.isDisplayed()))
        return true
    } catch (e: Throwable) {
        return false
    }
}

fun ViewInteraction.waitAndPerform(viewActions: ViewAction): ViewInteraction? {
    val startTime = System.currentTimeMillis()
    while (!isDisplayed()) {
        Thread.sleep(100)
        if (System.currentTimeMillis() - startTime >= 5000) {
            throw AssertionError("View not visible after 5000 milliseconds")
        }
    }
    return perform(viewActions)
}

