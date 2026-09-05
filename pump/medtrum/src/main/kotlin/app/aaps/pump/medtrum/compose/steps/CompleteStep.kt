package app.aaps.pump.medtrum.compose.steps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import app.aaps.pump.medtrum.compose.MedtrumPatchViewModel

/**
 * COMPLETE and CANCEL steps both finish the activity immediately.
 * This composable just triggers the finish event.
 */
@Composable
fun CompleteStep(viewModel: MedtrumPatchViewModel) {
    LaunchedEffect(Unit) {
        viewModel.handleComplete()
    }
}
