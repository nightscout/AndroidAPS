package info.nightscout.androidaps

import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers

fun ViewInteraction.isDisplayed(): Boolean {
    try {
        check(matches(ViewMatchers.isDisplayed()))
        return true
    } catch (e: NoMatchingViewException) {
        return false
    }
}
