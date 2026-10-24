package app.aaps.appshell.navigation

import androidx.navigation.NavController
import app.aaps.core.interfaces.configuration.ConfigBuilder
import app.aaps.core.interfaces.navigation.ElementType
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.AuthorizationResult
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.protection.ProtectionResult
import app.aaps.core.interfaces.source.DexcomBoyda
import app.aaps.core.ui.compose.ScreenMode
import app.aaps.core.ui.compose.navigation.NavigationRequest
import app.aaps.ui.compose.main.MainViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mockingDetails
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * The drawer, the toolbar and search all funnel through [ElementNavigator], so a dropped branch here
 * is a menu entry that silently does nothing - the bug the class was extracted to fix.
 *
 * Assertions read the recorded invocations rather than using `verify` with argument matchers.
 * `NavController.navigate` takes two further parameters that Kotlin fills with defaults, so a single
 * matcher there throws `InvalidUseOfMatchersException` - and that exception then leaks into whichever
 * test Mockito checks next, which is how three unrelated tests failed at once while it was written
 * that way.
 */
class ElementNavigatorTest {

    private lateinit var navController: NavController
    private lateinit var mainViewModel: MainViewModel
    private lateinit var activePlugin: ActivePlugin
    private lateinit var protectionCheck: ProtectionCheck
    private lateinit var configBuilder: ConfigBuilder
    private lateinit var dexcomBoyda: DexcomBoyda

    private val openedCgmApps = mutableListOf<String>()
    private var exited = false

    private lateinit var sut: ElementNavigator

    @BeforeEach
    fun setUp() {
        navController = mock()
        mainViewModel = mock()
        activePlugin = mock()
        protectionCheck = mock()
        configBuilder = mock()
        dexcomBoyda = mock()
        openedCgmApps.clear()
        exited = false
        sut = ElementNavigator(
            navController = navController,
            mainViewModel = mainViewModel,
            activePlugin = activePlugin,
            protectionCheck = protectionCheck,
            configBuilder = configBuilder,
            dexcomBoyda = dexcomBoyda,
            onOpenCgmApp = { openedCgmApps += it },
            onExit = { exited = true },
            onRequestDirectoryAccess = {},
            onOpenUrl = {}
        )
    }

    /** Routes passed to `navigate`, in call order. */
    private fun navigatedRoutes(): List<String> =
        mockingDetails(navController).invocations
            .filter { it.method.name == "navigate" }
            .mapNotNull { it.arguments.firstOrNull() as? String }

    private fun viewModelCalls(): List<String> =
        mockingDetails(mainViewModel).invocations.map { it.method.name }

    /** Authorization always succeeds, granting [granted]. */
    private fun grantAuthorization(granted: ProtectionCheck.Protection?) {
        whenever(protectionCheck.requestAuthorization(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onResult = invocation.arguments[1] as (AuthorizationResult) -> Unit
            val outcome = if (granted != null) ProtectionResult.GRANTED else ProtectionResult.DENIED
            onResult(AuthorizationResult(grantedLevel = granted, outcome = outcome))
        }
    }

    /** Protection always resolves to [outcome]. */
    private fun resolveProtection(outcome: ProtectionResult) {
        whenever(protectionCheck.requestProtection(any(), any())).thenAnswer { invocation ->
            @Suppress("UNCHECKED_CAST")
            val onResult = invocation.arguments[1] as (ProtectionResult) -> Unit
            onResult(outcome)
        }
    }

    // region guarded

    @Test
    fun `an unprotected action runs without asking for authorization`() {
        var ran = false

        sut.guarded(ProtectionCheck.Protection.NONE) { ran = true }

        assertThat(ran).isTrue()
        assertThat(mockingDetails(protectionCheck).invocations).isEmpty()
    }

    @Test
    fun `a protected action runs once protection is granted`() {
        resolveProtection(ProtectionResult.GRANTED)
        var ran = false

        sut.guarded(ProtectionCheck.Protection.BOLUS) { ran = true }

        assertThat(ran).isTrue()
    }

    @Test
    fun `a protected action does not run when protection is denied`() {
        resolveProtection(ProtectionResult.DENIED)
        var ran = false

        sut.guarded(ProtectionCheck.Protection.BOLUS) { ran = true }

        assertThat(ran).isFalse()
    }

    @Test
    fun `a protected action does not run when the dialog is cancelled`() {
        resolveProtection(ProtectionResult.CANCELLED)
        var ran = false

        sut.guarded(ProtectionCheck.Protection.MASTER) { ran = true }

        assertThat(ran).isFalse()
    }

    // endregion

    // region the granted level decides the screen mode

    /**
     * The subtle rule: the level that comes back decides whether the screen opens for editing or read
     * only, so granting less than PREFERENCES must not open an editable management screen.
     */
    @Test
    fun `granting preferences opens a management screen in edit mode`() {
        grantAuthorization(ProtectionCheck.Protection.PREFERENCES)

        sut.handleNavigationRequest(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT))

        assertThat(navigatedRoutes()).containsExactly(AppRoute.Profile.createRoute(ScreenMode.EDIT))
    }

    @Test
    fun `granting less than preferences opens a management screen read only`() {
        grantAuthorization(ProtectionCheck.Protection.BOLUS)

        sut.handleNavigationRequest(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT))

        assertThat(navigatedRoutes()).containsExactly(AppRoute.Profile.createRoute(ScreenMode.PLAY))
    }

    @Test
    fun `a denied authorization navigates nowhere`() {
        grantAuthorization(null)

        sut.handleNavigationRequest(NavigationRequest.Element(ElementType.PROFILE_MANAGEMENT))

        assertThat(navigatedRoutes()).isEmpty()
    }

    // endregion

    // region request types

    @Test
    fun `a quick wizard request executes that wizard`() {
        resolveProtection(ProtectionResult.GRANTED)

        sut.handleNavigationRequest(NavigationRequest.QuickWizard("guid-1"))

        assertThat(viewModelCalls()).contains("executeQuickWizard")
    }

    @Test
    fun `a quick wizard request does nothing when protection is refused`() {
        resolveProtection(ProtectionResult.DENIED)

        sut.handleNavigationRequest(NavigationRequest.QuickWizard("guid-1"))

        assertThat(viewModelCalls()).doesNotContain("executeQuickWizard")
    }

    @Test
    fun `a plugin request for a class that is not loaded navigates nowhere`() {
        whenever(activePlugin.getPluginsList()).thenReturn(ArrayList())

        sut.handleNavigationRequest(NavigationRequest.Plugin("NoSuchPlugin"))

        assertThat(navigatedRoutes()).isEmpty()
    }

    @Test
    fun `a plugin preferences request opens that plugin's preferences`() {
        resolveProtection(ProtectionResult.GRANTED)

        sut.handleNavigationRequest(NavigationRequest.PluginPreferences("SomeKey"))

        assertThat(navigatedRoutes()).containsExactly(AppRoute.PluginPreferences.createRoute("SomeKey"))
    }

    // endregion

    // region the element mapping

    /**
     * Every element that is meant to lead somewhere does. Authorization is granted at the highest level
     * so protection never masks a missing branch, and the types that deliberately do nothing are named,
     * so moving one in or out of that group fails here rather than on the device.
     */
    @Test
    fun `every element type either navigates, acts, or is a declared no-op`() {
        val declaredNoOps = setOf(
            ElementType.QUICK_WIZARD,
            ElementType.SCENE,
            ElementType.AUTOMATION,
            ElementType.COB,
            ElementType.SENSITIVITY,
            ElementType.USER_ENTRY,
            ElementType.LOOP,
            ElementType.AAPS
        )
        // PUMP goes through the active pump plugin, which needs a plugin list rather than a bare mock.
        val exercisedElsewhere = setOf(ElementType.PUMP)

        val inert = mutableListOf<ElementType>()
        for (type in ElementType.entries) {
            if (type in exercisedElsewhere) continue
            setUp()
            grantAuthorization(ProtectionCheck.Protection.MASTER)
            resolveProtection(ProtectionResult.GRANTED)
            whenever(dexcomBoyda.dexcomPackages()).thenReturn(listOf("com.dexcom.g6"))

            sut.handleNavigationRequest(NavigationRequest.Element(type))

            val didSomething = navigatedRoutes().isNotEmpty() ||
                openedCgmApps.isNotEmpty() ||
                exited ||
                viewModelCalls().isNotEmpty()
            if (!didSomething) inert += type
        }

        assertThat(inert).containsExactlyElementsIn(declaredNoOps)
    }

    @Test
    fun `exiting closes the ui before the app records the exit`() {
        grantAuthorization(ProtectionCheck.Protection.MASTER)

        sut.handleNavigationRequest(NavigationRequest.Element(ElementType.EXIT))

        assertThat(exited).isTrue()
        assertThat(mockingDetails(configBuilder).invocations.map { it.method.name }).contains("exitApp")
    }

    @Test
    fun `the xdrip element opens the xdrip package`() {
        grantAuthorization(ProtectionCheck.Protection.MASTER)

        sut.handleNavigationRequest(NavigationRequest.Element(ElementType.CGM_XDRIP))

        assertThat(openedCgmApps).containsExactly("com.eveningoutpost.dexdrip")
    }

    @Test
    fun `the dexcom element opens every package the source reports`() {
        grantAuthorization(ProtectionCheck.Protection.MASTER)
        whenever(dexcomBoyda.dexcomPackages()).thenReturn(listOf("com.dexcom.g6", "com.dexcom.g7"))

        sut.handleNavigationRequest(NavigationRequest.Element(ElementType.CGM_DEX))

        assertThat(openedCgmApps).containsExactly("com.dexcom.g6", "com.dexcom.g7").inOrder()
    }

    // endregion
}
