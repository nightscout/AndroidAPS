package app.aaps.plugins.source

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class DexcomPluginTest : TestBaseWithProfile() {

    private lateinit var dexcomPlugin: DexcomPlugin

    @BeforeEach
    fun setup() {
        dexcomPlugin = DexcomPlugin(rh, aapsLogger, context, config, preferences)
    }

    @Test
    fun `requiredPermissions should include dexcom permission when app is installed`() {
        val mockPm = mock<PackageManager> {
            @Suppress("DEPRECATION")
            on { getPackageInfo(eq("com.dexcom.g6"), any<Int>()) } doReturn PackageInfo()
        }
        whenever(context.packageManager).thenReturn(mockPm)

        val allPermissions = dexcomPlugin.requiredPermissions().flatMap { it.permissions }
        assertThat(allPermissions).contains(DexcomPlugin.PERMISSION)
    }

    @Test
    fun `requiredPermissions should be empty when no dexcom app is installed`() {
        val allPermissions = dexcomPlugin.requiredPermissions().flatMap { it.permissions }
        assertThat(allPermissions).doesNotContain(DexcomPlugin.PERMISSION)
    }
}
