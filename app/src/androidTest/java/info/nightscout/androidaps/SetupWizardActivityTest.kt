package info.nightscout.androidaps

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withClassName
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withTagValue
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.test.rule.GrantPermissionRule
import info.nightscout.androidaps.interfaces.PluginType
import info.nightscout.androidaps.plugins.aps.loop.LoopPlugin
import info.nightscout.androidaps.plugins.aps.openAPSSMB.OpenAPSSMBPlugin
import info.nightscout.androidaps.plugins.configBuilder.ProfileFunctions
import info.nightscout.androidaps.plugins.constraints.objectives.ObjectivesPlugin
import info.nightscout.androidaps.plugins.profile.local.LocalProfilePlugin
import info.nightscout.androidaps.plugins.pump.virtual.VirtualPumpPlugin
import info.nightscout.androidaps.plugins.sensitivity.SensitivityOref1Plugin
import info.nightscout.androidaps.plugins.source.RandomBgPlugin
import info.nightscout.androidaps.setupwizard.SetupWizardActivity
import info.nightscout.androidaps.utils.HardLimits
import info.nightscout.androidaps.utils.SP
import info.nightscout.androidaps.utils.isRunningTest
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert
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
    var mGrantPermissionRule: GrantPermissionRule =
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
        SP.clear()
        Assert.assertTrue(isRunningTest())
        // Welcome page
        onView(withId(R.id.next_button)).perform(click())
        // Language selection
        onView(withText("English")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Agreement page
        onView(withText("I UNDERSTAND AND AGREE")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Location permission
        var askButton = onView(withText("Ask for permission"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(withId(R.id.next_button)).waitAndPerform(click())
        }
        // Store permission
        askButton = onView(withText("Ask for permission"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(withText("OK")).perform(click())
            onView(withId(R.id.next_button)).waitAndPerform(click())
        }
        // Import settings : skip of found
        askButton = onView(withText("IMPORT SETTINGS"))
        if (askButton.isDisplayed()) {
            onView(withId(R.id.next_button)).waitAndPerform(click())
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
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Insulin selection
        onView(withText("Ultra-Rapid Oref")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // BG source selection
        onView(withText("Random BG")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Profile selection
        onView(withText("Local Profile")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
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
        // confirm dialog
        //onView(Matchers.allOf(withText("OK"), isDisplayed())).perform(click()) not working on real phone
        clickOkInDialog()
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Profile switch
        askButton = onView(withText("Do Profile Switch"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(allOf(withId(R.id.ok), isDisplayed())).perform(click())
            // onView(Matchers.allOf(withText("OK"), isDisplayed())).perform(click()) not working on real phone
            clickOkInDialog()
            while (ProfileFunctions.getInstance().profile == null) SystemClock.sleep(100)
            onView(withId(R.id.next_button)).waitAndPerform(click())
        }
        // Pump
        onView(withText("Virtual Pump")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // APS
        onView(withText("OpenAPS SMB")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Open Closed Loop
        onView(withText("Closed Loop")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Loop
        askButton = onView(withText("Enable loop"))
        if (askButton.isDisplayed()) {
            askButton.perform(scrollTo(), click())
            onView(withId(R.id.next_button)).waitAndPerform(click())
        }
        // Sensitivity
        onView(withText("Sensitivity Oref1")).perform(scrollTo(), click())
        onView(withId(R.id.next_button)).waitAndPerform(click())
        // Objectives
        onView(allOf(withText("Start"), isDisplayed())).perform(scrollTo(), click())
        onView(withId(R.id.finish_button)).waitAndPerform(click())

        // Verify settings
        Assert.assertEquals(Constants.MMOL, ProfileFunctions.getSystemUnits())
        Assert.assertEquals(17.0, HardLimits.maxBolus(), 0.0001) // Adult
        Assert.assertTrue(RandomBgPlugin.isEnabled(PluginType.BGSOURCE))
        Assert.assertTrue(LocalProfilePlugin.isEnabled(PluginType.PROFILE))
        val p = ProfileFunctions.getInstance().profile
        Assert.assertNotNull(p)
        Assert.assertEquals(2.0, p!!.ic, 0.0001)
        Assert.assertEquals(3.0 * Constants.MMOLL_TO_MGDL, p.isfMgdl, 0.0001)
        Assert.assertEquals(1.1, p.getBasalTimeFromMidnight(0), 0.0001)
        Assert.assertEquals(6.0 * Constants.MMOLL_TO_MGDL, p.targetLowMgdl, 0.0001)
        Assert.assertTrue(VirtualPumpPlugin.getPlugin().isEnabled(PluginType.PUMP))
        Assert.assertTrue(OpenAPSSMBPlugin.getPlugin().isEnabled(PluginType.APS))
        Assert.assertTrue(LoopPlugin.getPlugin().isEnabled(PluginType.LOOP))
        Assert.assertTrue(SensitivityOref1Plugin.getPlugin().isEnabled(PluginType.SENSITIVITY))
        Assert.assertTrue(ObjectivesPlugin.objectives[0].isStarted)
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
