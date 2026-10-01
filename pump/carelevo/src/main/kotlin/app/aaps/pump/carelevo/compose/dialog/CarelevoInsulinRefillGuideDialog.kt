package app.aaps.pump.carelevo.compose.dialog

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.aaps.core.ui.R as CoreUiR
import app.aaps.core.ui.compose.AapsSpacing
import app.aaps.pump.carelevo.R
import kotlinx.coroutines.launch

private data class RefillStep(@DrawableRes val image: Int, @StringRes val text: Int)

private val REFILL_STEPS = listOf(
    RefillStep(R.drawable.carelevo_refill_step_01, R.string.carelevo_insulin_refill_step1),
    RefillStep(R.drawable.carelevo_refill_step_02, R.string.carelevo_insulin_refill_step2),
    RefillStep(R.drawable.carelevo_refill_step_03, R.string.carelevo_insulin_refill_step3),
    RefillStep(R.drawable.carelevo_refill_step_04, R.string.carelevo_insulin_refill_step4),
    RefillStep(R.drawable.carelevo_refill_step_05, R.string.carelevo_insulin_refill_step5),
    RefillStep(R.drawable.carelevo_refill_step_06, R.string.carelevo_insulin_refill_step6),
    RefillStep(R.drawable.carelevo_refill_step_07, R.string.carelevo_insulin_refill_step7),
    RefillStep(R.drawable.carelevo_refill_step_08, R.string.carelevo_insulin_refill_step8),
    RefillStep(R.drawable.carelevo_refill_step_09, R.string.carelevo_insulin_refill_step9),
    RefillStep(R.drawable.carelevo_refill_step_10, R.string.carelevo_insulin_refill_step10),
    RefillStep(R.drawable.carelevo_refill_step_11, R.string.carelevo_insulin_refill_step11),
    RefillStep(R.drawable.carelevo_refill_step_12, R.string.carelevo_insulin_refill_step12)
)

// Diagram aspect runs 2.1:1 to 1:1.5 and the sentences one to five lines, so both are fixed —
// otherwise the dialog resizes on every swipe.
private val GUIDE_IMAGE_HEIGHT = 220.dp
private val GUIDE_PAGE_HEIGHT = 340.dp

/** The insulin-refill instructions, one step at a time. */
@Composable
internal fun CarelevoInsulinRefillGuideDialog(onDismissRequest: () -> Unit) {
    val pagerState = rememberPagerState { REFILL_STEPS.size }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = stringResource(R.string.carelevo_btn_insulin_guide)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(AapsSpacing.large)) {
                HorizontalPager(state = pagerState) { page ->
                    val step = REFILL_STEPS[page]
                    Column(
                        verticalArrangement = Arrangement.spacedBy(AapsSpacing.large),
                        modifier = Modifier.height(GUIDE_PAGE_HEIGHT)
                    ) {
                        Image(
                            painter = painterResource(step.image),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            // Single-colour alpha mask, so a tint gives light-on-dark for free.
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(GUIDE_IMAGE_HEIGHT)
                        )
                        Text(
                            text = stringResource(step.text),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icons, not labels — "Next" as text would read as the dialog's own action.
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateBefore,
                            contentDescription = stringResource(CoreUiR.string.back)
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.carelevo_insulin_refill_step_position,
                            pagerState.currentPage + 1,
                            REFILL_STEPS.size
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                        enabled = pagerState.currentPage < REFILL_STEPS.lastIndex
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                            contentDescription = stringResource(CoreUiR.string.next)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(text = stringResource(CoreUiR.string.close))
            }
        }
    )
}
