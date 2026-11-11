package dev.nocny.lessive.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.DiagState
import dev.nocny.lessive.R
import dev.nocny.lessive.protocol.M24srSession.Step

/** Validates the full command path (unlock, write, GPO, acknowledgement) without water. */
@Composable
fun DiagnosticCard(state: DiagState, writeBlocked: Boolean, onRun: () -> Unit, onReset: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        AnimatedContent(state, contentKey = { it::class }, label = "diag") { s ->
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                when (s) {
                    DiagState.Idle -> {
                        Icon(Icons.Rounded.WaterDrop, null, Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.diag_title), style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(
                            stringResource(R.string.diag_text),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRun, enabled = !writeBlocked) { Text(stringResource(R.string.action_test)) }
                    }
                    is DiagState.Running -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(56.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                when (s.step) {
                                    null -> stringResource(R.string.place_phone)
                                    Step.Connecting, Step.Unlocking -> stringResource(R.string.diag_connecting)
                                    Step.Writing -> stringResource(R.string.diag_writing)
                                    Step.Signalling -> stringResource(R.string.step_signalling)
                                    Step.WaitingForMachine -> stringResource(R.string.diag_waiting)
                                },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        TextButton(onClick = onReset) { Text(stringResource(R.string.action_cancel)) }
                    }
                    is DiagState.Done -> {
                        Text(stringResource(R.string.diag_done_title), style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(
                            stringResource(R.string.diag_done_text),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(12.dp))
                        s.version?.let { Text(stringResource(R.string.diag_version, it), style = MaterialTheme.typography.bodyLarge) }
                        Text(stringResource(R.string.diag_total, s.counters.sum()), style = MaterialTheme.typography.bodyLarge)
                        val used = s.counters.withIndex().filter { it.value > 0 }
                        if (used.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.diag_by_position), style = MaterialTheme.typography.labelLarge)
                            used.forEach { (i, n) -> Text(stringResource(R.string.diag_position, i + 1, n), style = MaterialTheme.typography.bodyMedium) }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onReset) { Text(stringResource(R.string.action_ok)) }
                    }
                    is DiagState.Failed -> {
                        Text(s.headline, style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(s.detail, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Row {
                            Button(onClick = onRun, enabled = !writeBlocked) { Text(stringResource(R.string.action_retry)) }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = onReset) { Text(stringResource(R.string.action_close)) }
                        }
                    }
                }
            }
        }
    }
}
