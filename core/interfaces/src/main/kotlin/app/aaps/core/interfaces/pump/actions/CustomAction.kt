package app.aaps.core.interfaces.pump.actions

import androidx.compose.ui.graphics.vector.ImageVector

data class CustomAction(
    val name: Int,
    val customActionType: CustomActionType,
    val icon: ImageVector,
    var isEnabled: Boolean = true
)