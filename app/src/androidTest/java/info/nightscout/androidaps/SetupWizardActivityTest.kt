package info.nightscout.androidaps


import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.setupwizard.SetupWizardActivity
import info.nightscout.androidaps.utils.SP
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith


@LargeTest
@RunWith(AndroidJUnit4::class)
class SetupWizardActivityTest {

    @Rule
    @JvmField
    var mActivityTestRule = ActivityTestRule(SetupWizardActivity::class.java)

    @Rule
    @JvmField
    var mGrantPermissionRule =
            GrantPermissionRule.grant(
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            )

    @Before
    fun clear() {
        SP.clear()
    }
/*

To run from command line
gradlew connectedFullDebugAndroidTest

do not run when your production phone is connected !!!

do this before for running in emulator
adb shell settings put global window_animation_scale 0 &
adb shell settings put global transition_animation_scale 0 &
adb shell settings put global animator_duration_scale 0 &
 */


    @Test
    fun setupWizardActivityTest() {
        // Welcome page
        onView(withId(R.id.next_button)).perform(click())
        // Language selection
        onView(withText("English")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Agreement page
        onView(withText("I UNDERSTAND AND AGREE")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Loction permission
        var askButton = onView(withText("Ask for permission"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(withId(R.id.next_button)).perform(click())
        }
        // Store permission
        askButton = onView(withText("Ask for permission"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(withText("OK")).perform(click())
            onView(withId(R.id.next_button)).perform(click())
        }
        // Units selection
        onView(withText("mmol/L")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Display target selection
        onView(withText("4.2")).perform(scrollTo(), ViewActions.replaceText("5"))
        onView(withText("10.0")).perform(scrollTo(), ViewActions.replaceText("11"))
        onView(withId(R.id.next_button)).perform(click())
        // NSClient
        onView(withId(R.id.next_button)).perform(click())
        // Age selection
        onView(withText("Adult")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Insulin selection
        onView(withText("Ultra-Rapid Oref")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // BG source selection
        onView(withText("Random BG")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Profile selection
        onView(withText("Local Profile")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Local profile - DIA
        onView(withTagValue(Matchers.`is`("LP_DIA"))).perform(scrollTo(), ViewActions.replaceText("6.0"))
        // Local profile - IC
        onView(withId(R.id.ic_tab)).perform(scrollTo(), click())
        onView(Matchers.allOf(withTagValue(Matchers.`is`("IC-1-0")), isDisplayed()))
                .perform(ViewActions.replaceText("2"), ViewActions.closeSoftKeyboard())
        // Local profile - ISF
        onView(withId(R.id.isf_tab)).perform(scrollTo(), click())
        onView(Matchers.allOf(withTagValue(Matchers.`is`("ISF-1-0")), isDisplayed()))
                .perform(ViewActions.replaceText("3"), ViewActions.closeSoftKeyboard())
        // Local profile - BAS
        onView(withId(R.id.basal_tab)).perform(scrollTo(), click())
        onView(childAtPosition(Matchers.allOf(withId(R.id.localprofile_basal), childAtPosition(withClassName(Matchers.`is`("android.widget.LinearLayout")), 6)), 2))
                .perform(scrollTo(), click())
        onView(Matchers.allOf(withTagValue(Matchers.`is`("BASAL-1-0")), isDisplayed()))
                .perform(ViewActions.replaceText("1.1"), ViewActions.closeSoftKeyboard())
        onView(Matchers.allOf(withTagValue(Matchers.`is`("BASAL-1-1")), isDisplayed()))
                .perform(ViewActions.replaceText("1.2"), ViewActions.closeSoftKeyboard())
        onView(Matchers.allOf(withId(R.id.timelistedit_time), childAtPosition(childAtPosition(withId(R.id.localprofile_basal), 2), 0)))
                .perform(scrollTo(), click())
        onData(Matchers.anything()).inAdapterView(childAtPosition(withClassName(Matchers.`is`("android.widget.PopupWindow\$PopupBackgroundView")), 0)).atPosition(13)
                .perform(click())
        // Local profile - TARGET
        onView(withId(R.id.target_tab)).perform(scrollTo(), click())
        onView(Matchers.allOf(withTagValue(Matchers.`is`("TARGET-1-0")), isDisplayed()))
                .perform(ViewActions.replaceText("6"), ViewActions.closeSoftKeyboard())
        onView(Matchers.allOf(withTagValue(Matchers.`is`("TARGET-2-0")), isDisplayed()))
                .perform(ViewActions.replaceText("6.5"), ViewActions.closeSoftKeyboard())
        onView(withText("Save")).perform(scrollTo(), click())
        onView(Matchers.allOf(withId(R.id.localprofile_profileswitch), isDisplayed()))
                .perform(scrollTo(), click())
        onView(allOf(withId(R.id.ok), isDisplayed())).perform(click())
        onView(Matchers.allOf(withText("OK"), isDisplayed())).perform(click())
        onView(withId(R.id.next_button)).perform(click())
        // Profile switch
        askButton = onView(withText("Do Profile Switch"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(allOf(withId(R.id.ok), isDisplayed())).perform(click())
            onView(Matchers.allOf(withText("OK"), isDisplayed())).perform(click())
            while (ProfileFunctions.getInstance().profile == null) SystemClock.sleep(100)
            onView(withId(R.id.next_button)).perform(click())
        }
        // Pump
        onView(withText("Virtual Pump")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // APS
        onView(withText("OpenAPS SMB")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Open Closed Loop
        onView(withText("Closed Loop")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Loop
        askButton = onView(withText("Enable loop"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(withId(R.id.next_button)).perform(click())
        }
        // Sensitivity
        onView(withText("Sensitivity Oref1")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).perform(click())
        // Objectives
        onView(allOf(withText("Start"), isDisplayed())).perform(scrollTo(), click())
    }

    private fun childAtPosition(
            parentMatcher: Matcher<View>, position: Int): Matcher<View> {

        return object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("Child at position $position in parent ")
                parentMatcher.describeTo(description)
            }

            public override fun matchesSafely(view: View): Boolean {
                val parent = view.parent
                return parent is ViewGroup && parentMatcher.matches(parent)
                        && view == parent.getChildAt(position)
            }
        }
    }
}
