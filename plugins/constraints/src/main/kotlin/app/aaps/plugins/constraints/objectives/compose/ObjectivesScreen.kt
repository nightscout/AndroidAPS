package app.aaps.plugins.constraints.objectives.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.aaps.plugins.constraints.R

@Composable
fun ObjectivesScreen(
    state: ObjectivesUiState,
    onFakeModeToggle: (Boolean) -> Unit,
    onReset: () -> Unit,
    onStart: (Int) -> Unit,
    onVerify: (Int) -> Unit,
    onRequestUnstart: (Int) -> Unit,
    onUnfinish: (Int) -> Unit,
    onShowLearned: (Int) -> Unit,
    onOpenExam: (objectiveIndex: Int, taskIndex: Int) -> Unit,
    onInvokeUITask: (android.content.Context, objectiveIndex: Int, taskIndex: Int) -> Unit,
    scrollToIndex: Int,
    onScrollHandled: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(scrollToIndex) {
        if (scrollToIndex >= 0) {
            listState.animateScrollToItem(scrollToIndex)
            onScrollHandled()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Debug controls
        if (state.showDebugControls) {
            DebugControls(
                isFakeMode = state.isFakeMode,
                onFakeModeToggle = onFakeModeToggle,
                onReset = onReset
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(
                items = state.objectives,
                key = { _, item -> item.index }
            ) { index, objective ->
                ObjectiveTimelineItem(
                    objective = objective,
                    isLast = index == state.objectives.lastIndex,
                    isFakeMode = state.isFakeMode,
                    onStart = { onStart(objective.index) },
                    onVerify = { onVerify(objective.index) },
                    onRequestUnstart = { onRequestUnstart(objective.index) },
                    onUnfinish = { onUnfinish(objective.index) },
                    onShowLearned = { onShowLearned(objective.index) },
                    onOpenExam = { taskIndex -> onOpenExam(objective.index, taskIndex) },
                    onInvokeUITask = { context, taskIndex -> onInvokeUITask(context, objective.index, taskIndex) }
                )
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun DebugControls(
    isFakeMode: Boolean,
    onFakeModeToggle: (Boolean) -> Unit,
    onReset: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Switch(
                checked = isFakeMode,
                onCheckedChange = onFakeModeToggle
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Enable fake time and progress",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        OutlinedButton(onClick = onReset) {
            Icon(
                imageVector = Icons.Default.RestartAlt,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reset")
        }
    }
    HorizontalDivider()
}

@Composable
private fun ObjectiveTimelineItem(
    objective: ObjectiveUiItem,
    isLast: Boolean,
    isFakeMode: Boolean,
    onStart: () -> Unit,
    onVerify: () -> Unit,
    onRequestUnstart: () -> Unit,
    onUnfinish: () -> Unit,
    onShowLearned: () -> Unit,
    onOpenExam: (taskIndex: Int) -> Unit,
    onInvokeUITask: (android.content.Context, taskIndex: Int) -> Unit
) {
    val accomplishedColor = MaterialTheme.colorScheme.primary
    val activeColor = MaterialTheme.colorScheme.tertiary
    val lockedColor = MaterialTheme.colorScheme.outlineVariant
    val notStartedColor = MaterialTheme.colorScheme.outline

    val circleColor = when (objective.state) {
        ObjectiveState.ACCOMPLISHED -> accomplishedColor
        ObjectiveState.STARTED      -> activeColor
        ObjectiveState.NOT_STARTED  -> notStartedColor
        ObjectiveState.LOCKED       -> lockedColor
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        // Timeline column: circle + connector line
        TimelineIndicator(
            circleColor = circleColor,
            isAccomplished = objective.state == ObjectiveState.ACCOMPLISHED,
            isActive = objective.state == ObjectiveState.STARTED,
            isLast = isLast,
            lineColor = if (objective.state == ObjectiveState.ACCOMPLISHED) accomplishedColor else lockedColor
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Content column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 16.dp)
                .animateContentSize()
        ) {
            when (objective.state) {
                ObjectiveState.ACCOMPLISHED -> AccomplishedObjectiveContent(
                    objective = objective,
                    onShowLearned = onShowLearned,
                    onUnfinish = onUnfinish
                )

                ObjectiveState.STARTED      -> ActiveObjectiveContent(
                    objective = objective,
                    isFakeMode = isFakeMode,
                    onVerify = onVerify,
                    onRequestUnstart = onRequestUnstart,
                    onOpenExam = onOpenExam,
                    onInvokeUITask = onInvokeUITask
                )

                ObjectiveState.NOT_STARTED  -> NotStartedObjectiveContent(
                    objective = objective,
                    onStart = onStart
                )

                ObjectiveState.LOCKED       -> LockedObjectiveContent(objective = objective)
            }
        }
    }
}

@Composable
private fun TimelineIndicator(
    circleColor: Color,
    isAccomplished: Boolean,
    isActive: Boolean,
    isLast: Boolean,
    lineColor: Color
) {
    val checkColor = MaterialTheme.colorScheme.onPrimary
    Box(
        modifier = Modifier
            .width(32.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerX = size.width / 2
            val circleRadius = if (isActive) 14.dp.toPx() else 12.dp.toPx()
            val circleY = circleRadius + 4.dp.toPx()

            // Draw connector line below circle
            if (!isLast) {
                drawLine(
                    color = lineColor,
                    start = Offset(centerX, circleY + circleRadius),
                    end = Offset(centerX, size.height),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Draw circle
            drawCircle(
                color = circleColor,
                radius = circleRadius,
                center = Offset(centerX, circleY)
            )

            // Draw check mark for accomplished
            if (isAccomplished) {
                val checkSize = circleRadius * 0.55f
                val checkStartX = centerX - checkSize * 0.6f
                val checkMidX = centerX - checkSize * 0.1f
                val checkEndX = centerX + checkSize * 0.7f
                val checkStartY = circleY + checkSize * 0.1f
                val checkMidY = circleY + checkSize * 0.5f
                val checkEndY = circleY - checkSize * 0.4f

                drawLine(
                    color = checkColor,
                    start = Offset(checkStartX, checkStartY),
                    end = Offset(checkMidX, checkMidY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = checkColor,
                    start = Offset(checkMidX, checkMidY),
                    end = Offset(checkEndX, checkEndY),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun AccomplishedObjectiveContent(
    objective: ObjectiveUiItem,
    onShowLearned: () -> Unit,
    onUnfinish: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column {
        // Title row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = objective.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                objective.accomplishedOn?.let { date ->
                    Text(
                        text = date,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (objective.learned.isNotEmpty()) {
                TextButton(onClick = onShowLearned) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.what_i_ve_learned),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        // Description (collapsed by default)
        objective.description?.let { desc ->
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    TextButton(onClick = onUnfinish) {
                        Text(stringResource(R.string.objectives_button_unfinish))
                    }
                }
            }
        }

        // Expand/collapse toggle
        Row(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (expanded) stringResource(R.string.objectives_collapse) else stringResource(R.string.objectives_expand),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveObjectiveContent(
    objective: ObjectiveUiItem,
    isFakeMode: Boolean,
    onVerify: () -> Unit,
    onRequestUnstart: () -> Unit,
    onOpenExam: (taskIndex: Int) -> Unit,
    onInvokeUITask: (android.content.Context, taskIndex: Int) -> Unit
) {
    val context = LocalContext.current

    Column {
        // Title
        Text(
            text = objective.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Description
        objective.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // Gate
        objective.gate?.let { gate ->
            Text(
                text = gate,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Task card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                objective.tasks.forEachIndexed { index, task ->
                    TaskRow(
                        task = task,
                        onOpenExam = { onOpenExam(task.index) },
                        onInvokeUITask = { onInvokeUITask(context, task.index) }
                    )
                    if (index < objective.tasks.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }

                // Progress bar
                if (objective.totalTaskCount > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LinearProgressIndicator(
                            progress = { objective.progress },
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            text = "${objective.completedTaskCount}/${objective.totalTaskCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRequestUnstart,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(stringResource(R.string.objectives_button_unstart))
                    }
                    Button(
                        onClick = onVerify,
                        enabled = objective.progress >= 1f || isFakeMode,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Text(stringResource(R.string.objectives_button_verify))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: TaskUiItem,
    onOpenExam: () -> Unit,
    onInvokeUITask: () -> Unit
) {
    var hintsExpanded by remember { mutableStateOf(false) }

    val completedColor = MaterialTheme.colorScheme.primary
    val pendingColor = MaterialTheme.colorScheme.outline

    val isInteractive = !task.isCompleted && (task.type == TaskType.EXAM || task.type == TaskType.UI_TASK)
    val rowModifier = if (isInteractive) {
        Modifier
            .fillMaxWidth()
            .clickable {
                when (task.type) {
                    TaskType.EXAM    -> onOpenExam()
                    TaskType.UI_TASK -> onInvokeUITask()
                }
            }
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.Top
    ) {
        // Status icon
        Icon(
            imageVector = if (task.isCompleted) Icons.Default.Check else Icons.Default.ChevronRight,
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp),
            tint = if (task.isCompleted) completedColor else pendingColor
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Task name
            Text(
                text = task.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (task.isCompleted)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Progress text
            Text(
                text = task.progress,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (task.isCompleted) completedColor else MaterialTheme.colorScheme.error
            )
            // Hints (expandable, only when not completed)
            if (!task.isCompleted && task.hints.isNotEmpty()) {
                TextButton(
                    onClick = { hintsExpanded = !hintsExpanded },
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Icon(
                        imageVector = if (hintsExpanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.objectives_show_hint),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                AnimatedVisibility(
                    visible = hintsExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        task.hints.forEach { hint ->
                            HintText(text = hint.text)
                        }
                    }
                }
            }
        }
        // Chevron indicator for interactive tasks
        if (isInteractive) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HintText(text: String) {
    val uriHandler = LocalUriHandler.current
    // Extract URL if present
    val urlRegex = remember { Regex("https?://\\S+") }
    val url = urlRegex.find(text)?.value

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (url != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (url != null) {
            Modifier
                .clickable { uriHandler.openUri(url) }
                .padding(vertical = 2.dp)
        } else {
            Modifier.padding(vertical = 2.dp)
        }
    )
}

@Composable
private fun NotStartedObjectiveContent(
    objective: ObjectiveUiItem,
    onStart: () -> Unit
) {
    Column {
        Text(
            text = objective.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        objective.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        objective.gate?.let { gate ->
            Text(
                text = gate,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onStart) {
            Text(stringResource(R.string.objectives_button_start))
        }
    }
}

@Composable
private fun LockedObjectiveContent(
    objective: ObjectiveUiItem
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = objective.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
        }
        objective.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outlineVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
