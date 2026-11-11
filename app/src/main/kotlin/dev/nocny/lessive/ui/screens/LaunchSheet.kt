package dev.nocny.lessive.ui.screens

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import dev.nocny.lessive.LaunchState
import dev.nocny.lessive.R
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.protocol.M24srSession.Step
import dev.nocny.lessive.ui.components.ProgramAvatar
import dev.nocny.lessive.ui.components.formatHour

@Composable
fun LaunchSheet(
    state: LaunchState,
    program: DialProgram?,
    summary: String?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state is LaunchState.Idle) return
    val busy = state is LaunchState.Running
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Never let a swipe close the sheet while the phone is talking to the machine.
        confirmValueChange = { !busy },
    )
    val view = LocalView.current
    LaunchedEffect(state::class) {
        when (state) {
            is LaunchState.Success -> view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            is LaunchState.Failed -> view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            is LaunchState.Running -> view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            else -> Unit
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        sheetGesturesEnabled = !busy,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (program != null) {
                    ProgramAvatar(program, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(state.title, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    summary?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            AnimatedContent(
                targetState = state,
                contentKey = { it is LaunchState.Failed },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "launch",
            ) { s ->
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (s is LaunchState.Failed) Failure(s, onDismiss, onRetry) else Progress(s, onDismiss)
                }
            }
        }
    }
}

private val LaunchState.title
    get() = when (this) {
        is LaunchState.WaitingForTag -> title
        is LaunchState.Running -> title
        is LaunchState.Success -> title
        is LaunchState.Failed -> title
        LaunchState.Idle -> ""
    }

@Composable
private fun Progress(state: LaunchState, onClose: () -> Unit) {
    val step = when (state) {
        is LaunchState.Running -> 1
        is LaunchState.Success -> 2
        else -> 0
    }
    val target = when (state) {
        is LaunchState.Running -> when (state.step) {
            Step.Connecting -> 0.15f
            Step.Unlocking -> 0.3f
            Step.Writing -> 0.5f
            Step.Signalling -> 0.7f
            Step.WaitingForMachine -> 0.88f
        }
        is LaunchState.Success -> 1f
        else -> 0.04f
    }
    val progress by animateFloatAsState(target, label = "progress")
    val context = LocalContext.current

    TapIllustration(listening = state !is LaunchState.Success, done = state is LaunchState.Success)

    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            when (state) {
                is LaunchState.WaitingForTag -> stringResource(if (state.retrying) R.string.tap_closer else R.string.tap_place)
                is LaunchState.Running -> stringResource(R.string.tap_hold)
                else -> stringResource(R.string.tap_started)
            },
            style = MaterialTheme.typography.headlineMediumEmphasized,
            textAlign = TextAlign.Center,
        )
        Text(
            when (state) {
                is LaunchState.WaitingForTag ->
                    stringResource(if (state.retrying) R.string.tap_lost else R.string.tap_how)
                is LaunchState.Running -> when (state.step) {
                    Step.Connecting -> stringResource(R.string.step_connecting)
                    Step.Unlocking -> stringResource(R.string.step_unlocking)
                    Step.Writing -> stringResource(R.string.step_writing)
                    Step.Signalling -> stringResource(R.string.step_signalling)
                    Step.WaitingForMachine -> stringResource(R.string.step_confirming)
                }
                is LaunchState.Success -> listOfNotNull(
                    stringResource(R.string.press_start),
                    state.endsAt?.let { stringResource(R.string.end_notify, context.formatHour(it)) },
                ).joinToString(" ")
                else -> ""
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp),
        )
    }

    LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    Steps(step)
    when (state) {
        is LaunchState.Success -> Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) { Text(stringResource(R.string.action_done), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
        is LaunchState.WaitingForTag -> OutlinedButton(onClick = onClose) { Text(stringResource(R.string.action_cancel)) }
        else -> Unit
    }
}

@Composable
private fun Steps(current: Int) {
    Row(Modifier.fillMaxWidth()) {
        listOf(R.string.step_approach, R.string.tap_hold, R.string.tap_started).map { stringResource(it) }.forEachIndexed { index, label ->
            val done = index < current || current == 2
            val active = index == current
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.size(28.dp).background(
                        if (done || active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    val tint = if (done || active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    if (done) Icon(Icons.Rounded.Check, null, tint = tint, modifier = Modifier.size(16.dp))
                    else Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = tint)
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** The top of the phone's back over the NFC logo on the control panel. */
@Composable
private fun TapIllustration(listening: Boolean, done: Boolean) {
    val c = MaterialTheme.colorScheme
    val pulse = rememberInfiniteTransition(label = "pulse")
    val wave by pulse.animateFloat(0f, 1f, infiniteRepeatable(tween(1600), RepeatMode.Restart), label = "wave")

    val description = stringResource(R.string.tap_illustration)
    Box(
        Modifier
            .size(260.dp)
            .semantics { contentDescription = description },
    ) {
        // Machine: panel, control strip, door.
        Box(
            Modifier.offset(16.dp, 130.dp).size(228.dp, 130.dp)
                .background(c.surfaceContainerHigh, RoundedCornerShape(24.dp, 24.dp, 8.dp, 8.dp)),
        )
        Row(
            Modifier.offset(30.dp, 146.dp).size(200.dp, 46.dp)
                .background(c.surfaceContainerHighest, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(34.dp).border(3.dp, c.outline, CircleShape))
            Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(9.dp).background(c.surfaceContainer, RoundedCornerShape(6.dp)))
            Box(Modifier.size(34.dp).background(c.primary, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Nfc, null, tint = c.onPrimary, modifier = Modifier.size(22.dp))
            }
        }
        Box(Modifier.offset(92.dp, 204.dp).size(76.dp).graphicsLayer { alpha = 0.5f }.border(3.dp, c.outline, CircleShape))

        if (listening) {
            Ring(c.primary, x = 182.dp, y = 132.dp, base = 60, grow = wave)
            Ring(c.primary, x = 182.dp, y = 132.dp, base = 60, grow = (wave + 0.5f) % 1f)
        }

        // Phone, tilted, chip area marked on its back.
        Box(
            Modifier.offset(150.dp, 8.dp).size(84.dp, 166.dp)
                .rotate(-12f)
                .shadow(12.dp, RoundedCornerShape(20.dp))
                .background(c.onSurfaceVariant, RoundedCornerShape(20.dp)),
        ) {
            Box(Modifier.offset(9.dp, 9.dp).size(30.dp, 16.dp).background(c.surface, RoundedCornerShape(8.dp)))
            Box(
                Modifier.offset(15.dp, 36.dp).size(54.dp, 38.dp).drawBehind {
                    drawRoundRect(
                        color = if (done) c.primaryContainer else c.primary,
                        cornerRadius = CornerRadius(size.height / 2),
                        style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f))),
                    )
                },
            )
        }
    }
}

@Composable
private fun Ring(color: Color, x: Dp, y: Dp, base: Int, grow: Float) {
    val size = (base + 50 * grow).dp
    Box(
        Modifier
            .offset(x - size / 2, y - size / 2)
            .size(size)
            .graphicsLayer { alpha = (1f - grow) * 0.6f }
            .border(2.5.dp, color, CircleShape),
    )
}

@Composable
private fun Failure(state: LaunchState.Failed, onClose: () -> Unit, onRetry: () -> Unit) {
    Spacer(Modifier.height(24.dp))
    Badge(Icons.Rounded.PriorityHigh, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer, MaterialShapes.Burst)
    Text(state.headline, style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
    Text(
        state.detail,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    if (!state.locked) {
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
        ) { Text(stringResource(R.string.action_retry), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
    }
    OutlinedButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_close)) }
}

@Composable
private fun Badge(icon: ImageVector, container: Color, content: Color, shape: RoundedPolygon) {
    Box(
        modifier = Modifier.size(112.dp).background(container, shape.toShape()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(56.dp))
    }
}
