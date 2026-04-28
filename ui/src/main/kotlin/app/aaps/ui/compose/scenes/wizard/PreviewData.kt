package app.aaps.ui.compose.scenes.wizard

import app.aaps.core.data.model.SceneAction
import app.aaps.core.data.model.TE
import app.aaps.core.data.model.TT
import app.aaps.core.data.model.TTPreset
import app.aaps.core.ui.R
import app.aaps.ui.compose.scenes.SceneTemplate

internal val previewState = SceneWizardViewModel.WizardState(
    template = SceneTemplate.EXERCISE,
    profileEnabled = false,
    ttEnabled = true,
    smbEnabled = false,
    runningModeEnabled = false,
    carePortalEnabled = true,
    ttAction = SceneAction.TempTarget(reason = TT.Reason.ACTIVITY, targetMgdl = 140.0),
    carePortalAction = SceneAction.CarePortalEvent(type = TE.Type.EXERCISE),
    durationMinutes = 60,
    name = "Exercise",
    icon = "exercise"
)

internal val previewPresets = listOf(
    TTPreset(id = "1", nameRes = R.string.activity, reason = TT.Reason.ACTIVITY, targetValue = 140.0, duration = 3600000, isDeletable = false),
    TTPreset(id = "2", nameRes = R.string.eatingsoon, reason = TT.Reason.EATING_SOON, targetValue = 90.0, duration = 2700000, isDeletable = false)
)
