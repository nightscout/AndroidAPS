package app.aaps.core.ui.compose.pump

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview(showBackground = true, name = "ProfileGate - has profiles")
@Composable
internal fun ProfileGateHasStorePreview() {
    val host = object : ProfileGateStepHost {
        override val availableProfiles = MutableStateFlow(listOf("Default", "Sport", "Sick"))
        override val selectedProfile = MutableStateFlow<String?>("Default")
        override fun selectProfile(name: String) {}
        override fun activateSelectedProfile() {}
        override fun cancelGate() {}
    }
    MaterialTheme { ProfileGateWizardStep(host) }
}

@Preview(showBackground = true, name = "ProfileGate - no store")
@Composable
internal fun ProfileGateNoStorePreview() {
    val host = object : ProfileGateStepHost {
        override val availableProfiles = MutableStateFlow(emptyList<String>())
        override val selectedProfile = MutableStateFlow<String?>(null)
        override fun selectProfile(name: String) {}
        override fun activateSelectedProfile() {}
        override fun cancelGate() {}
    }
    MaterialTheme { ProfileGateWizardStep(host) }
}
