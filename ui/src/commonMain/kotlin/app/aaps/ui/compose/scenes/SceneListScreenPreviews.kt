package app.aaps.ui.compose.scenes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.data.model.Scene
import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.TT

@Preview(showBackground = true)
@Composable
internal fun SceneCardNormalPreview() {
    MaterialTheme {
        Surface {
            SceneCard(
                scene = Scene(
                    id = "1",
                    name = "Exercise",
                    icon = "exercise",
                    defaultDurationMinutes = 60,
                    actions = listOf(
                        SceneAction.TempTarget(reason = TT.Reason.ACTIVITY, targetMgdl = 140.0),
                        SceneAction.SmbToggle(enabled = false)
                    )
                ),
                subtitle = "2 actions, 1 hour",
                isActive = false,
                onActivate = {},
                onDeactivate = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun SceneCardActivePreview() {
    MaterialTheme {
        Surface {
            SceneCard(
                scene = Scene(
                    id = "2",
                    name = "Sick Day",
                    icon = "sick",
                    defaultDurationMinutes = 480,
                    actions = listOf(
                        SceneAction.TempTarget(reason = TT.Reason.CUSTOM, targetMgdl = 120.0)
                    )
                ),
                subtitle = "1 actions, 8 hours",
                isActive = true,
                onActivate = {},
                onDeactivate = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
internal fun SceneCardInvalidPreview() {
    MaterialTheme {
        Surface {
            SceneCard(
                scene = Scene(
                    id = "3",
                    name = "Broken Scene",
                    icon = "star",
                    defaultDurationMinutes = 60,
                    actions = listOf(
                        SceneAction.ProfileSwitch(profileName = "Deleted Profile")
                    )
                ),
                subtitle = "1 actions, 1 hour",
                isActive = false,
                isInvalid = true,
                onActivate = {},
                onDeactivate = {},
                onEdit = {},
                onDelete = {}
            )
        }
    }
}
