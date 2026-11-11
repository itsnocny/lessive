package dev.nocny.lessive.ui.screens

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.R
import dev.nocny.lessive.ReadState
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.data.Habits
import dev.nocny.lessive.data.HistoryEntry
import dev.nocny.lessive.data.MachineProfile
import dev.nocny.lessive.data.Outcome
import dev.nocny.lessive.data.WashSettings
import dev.nocny.lessive.protocol.toHex
import dev.nocny.lessive.ui.components.ProgramAvatar
import dev.nocny.lessive.ui.components.daysAgo
import dev.nocny.lessive.ui.components.formatDateTime
import dev.nocny.lessive.ui.components.formatDuration

@Composable
fun MachineScreen(
    profile: MachineProfile,
    habits: Habits,
    read: ReadState,
    diag: dev.nocny.lessive.DiagState,
    onDiagnose: () -> Unit,
    onResetDiag: () -> Unit,
    history: List<HistoryEntry>,
    journal: List<String>,
    writeBlocked: Boolean,
    outerPadding: PaddingValues,
    onStart: (String, WashSettings) -> Unit,
    onOpenHelp: () -> Unit,
    onRead: () -> Unit,
    onCancelRead: () -> Unit,
    onEditDial: () -> Unit,
    onUnblock: () -> Unit,
    onClearJournal: () -> Unit,
) {
    var confirmUnblock by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val autoClean = profile.programs.firstOrNull { it.catalogKey?.contains("AUTO_CLEAN") == true }
    val counts = remember(habits) { habits.counts(days = 30) }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = outerPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item {
            Column(Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) {
                Text(profile.model, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.machine_title), style = MaterialTheme.typography.headlineLargeEmphasized)
            }
        }
        if (autoClean != null) item {
            AutoCleanCard(autoClean, habits.lastRunOf(autoClean), enabled = !writeBlocked) {
                onStart(
                    autoClean.name,
                    WashSettings(
                        selector = autoClean.selector,
                        temperature = autoClean.temperatureDefault ?: 0,
                        spin = autoClean.spinDefault ?: 0,
                        soil = autoClean.soilDefault ?: 1,
                        programId = autoClean.key,
                    ),
                )
            }
        }
        item { UsageCard(counts) }
        item {
            Card(
                onClick = onOpenHelp,
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("E03", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.error_code_title), style = MaterialTheme.typography.titleMediumEmphasized)
                        Text(stringResource(R.string.error_code_text), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (writeBlocked) item {
            Banner(
                icon = { Icon(Icons.Rounded.LockOpen, null) },
                title = stringResource(R.string.blocked_title),
                text = stringResource(R.string.blocked_machine),
                action = stringResource(R.string.action_unblock) to { confirmUnblock = true },
            )
        }
        item { Title(stringResource(R.string.tools)) }
        item { MachineReadCard(read, onRead, onCancelRead) }
        item { DiagnosticCard(diag, writeBlocked, onDiagnose, onResetDiag) }
        item {
            SegmentedListItem(
                onClick = onEditDial,
                shapes = ListItemDefaults.segmentedShapes(0, 1),
                leadingContent = { Icon(Icons.Rounded.Tune, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                supportingContent = {
                    Text(stringResource(R.string.positions_summary, profile.programs.size, stringResource(if (profile.verified) R.string.dial_checked else R.string.dial_to_check)))
                },
            ) { Text(stringResource(R.string.dial_settings)) }
        }
        if (history.isNotEmpty()) {
            item { Title(stringResource(R.string.history)) }
            itemsIndexed(history.take(10)) { index, entry ->
                SegmentedListItem(
                    shapes = ListItemDefaults.segmentedShapes(index, minOf(10, history.size)),
                    leadingContent = { OutcomeIcon(entry.outcome) },
                    supportingContent = { Text("${context.formatDateTime(entry.at)} · ${stringResource(outcomeLabel(entry.outcome))}") },
                ) { Text(entry.programName) }
            }
        }
        item { JournalCard(journal, onClearJournal) }
    }
    if (confirmUnblock) {
        AlertDialog(
            onDismissRequest = { confirmUnblock = false },
            title = { Text(stringResource(R.string.unblock_title)) },
            text = { Text(stringResource(R.string.unblock_text)) },
            confirmButton = { TextButton(onClick = { confirmUnblock = false; onUnblock() }) { Text(stringResource(R.string.action_unblock)) } },
            dismissButton = { TextButton(onClick = { confirmUnblock = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun AutoCleanCard(program: DialProgram, lastRun: Long?, enabled: Boolean, onStart: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(32.dp), colors = CardDefaults.cardColors(containerColor = c.primaryContainer, contentColor = c.onPrimaryContainer)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgramAvatar(program, size = 52.dp, container = c.primary, content = c.onPrimary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        stringResource(if (lastRun == null || System.currentTimeMillis() - lastRun > 30 * 86_400_000L) R.string.autoclean_due else R.string.auto_clean),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        listOfNotNull(
                            lastRun?.let { stringResource(R.string.last_run, context.daysAgo(it)) } ?: stringResource(R.string.never_run),
                            program.durationMinutes?.let(::formatDuration),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            Text(
                stringResource(R.string.autoclean_text),
                style = MaterialTheme.typography.bodyMedium,
            )
            listOf(R.string.autoclean_step1, R.string.autoclean_step2, R.string.autoclean_step3).map { stringResource(it) }.forEachIndexed { i, step ->
                Row {
                    Box(
                        Modifier.size(22.dp).background(c.onPrimaryContainer.copy(alpha = 0.16f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { Text("${i + 1}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)) }
                    Spacer(Modifier.width(10.dp))
                    Text(step, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Button(
                onClick = onStart,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.autoclean_launch), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun UsageCard(counts: List<Pair<DialProgram, Int>>) {
    Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.your_programs), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.last_30_days), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (counts.isEmpty()) {
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.usage_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val most = counts.firstOrNull()?.second ?: 1
            counts.take(5).forEach { (program, count) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProgramAvatar(program, size = 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(program.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(
                            Modifier.fillMaxWidth(count / most.toFloat()).height(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.times, count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleSmallEmphasized,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
)

@Composable
fun MachineReadCard(read: ReadState, onRead: () -> Unit, onCancel: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        AnimatedContent(read, contentKey = { it::class }, label = "read") { state ->
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                when (state) {
                    ReadState.Idle -> {
                        Icon(Icons.Rounded.Nfc, null, Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.read_title), style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(
                            stringResource(R.string.read_text),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = onRead) { Text(stringResource(R.string.action_read)) }
                    }
                    ReadState.Waiting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LoadingIndicator(Modifier.size(56.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.place_phone), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        }
                        TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
                    }
                    is ReadState.Done -> {
                        Text(stringResource(R.string.machine_found), style = MaterialTheme.typography.titleLargeEmphasized)
                        Spacer(Modifier.height(8.dp))
                        Field(stringResource(R.string.field_code), state.status.applianceCode?.chunked(4)?.joinToString(" ") ?: stringResource(R.string.unreadable))
                        state.status.extraDigits?.let { Field(stringResource(R.string.field_status), it) }
                        Field(stringResource(R.string.field_crc), stringResource(if (state.status.crcValid) R.string.crc_valid else R.string.crc_unchecked))
                        state.status.uri?.let { Field(stringResource(R.string.field_link), it) }
                        state.commandFile?.let { Field(stringResource(R.string.field_last_command), it.toHex().ifEmpty { stringResource(R.string.empty) }, mono = true) }
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = onRead) { Text(stringResource(R.string.action_reread)) }
                    }
                    is ReadState.Failed -> {
                        Text(stringResource(R.string.read_failed), style = MaterialTheme.typography.titleLargeEmphasized)
                        Text(state.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRead) { Text(stringResource(R.string.action_retry)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            style = if (mono) MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun OutcomeIcon(outcome: Outcome) = when (outcome) {
    Outcome.Started -> Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
    Outcome.NoAnswer, Outcome.Interrupted -> Icon(Icons.Rounded.HourglassEmpty, null, tint = MaterialTheme.colorScheme.tertiary)
    Outcome.Refused -> Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
}

private fun outcomeLabel(outcome: Outcome) = when (outcome) {
    Outcome.Started -> R.string.outcome_started
    Outcome.NoAnswer -> R.string.outcome_no_answer
    Outcome.Interrupted -> R.string.outcome_interrupted
    Outcome.Refused -> R.string.outcome_refused
}

@Composable
private fun JournalCard(journal: List<String>, onClear: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.share_journal)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.journal_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.journal_summary, journal.size), style = MaterialTheme.typography.bodySmall)
                }
                IconButton(
                    enabled = journal.isNotEmpty(),
                    onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, journal.joinToString("\n"))
                        context.startActivity(Intent.createChooser(send, shareTitle))
                    },
                ) { Icon(Icons.Rounded.Share, stringResource(R.string.action_share)) }
            }
            if (journal.isNotEmpty()) {
                Row {
                    TextButton(onClick = { expanded = !expanded }) { Text(stringResource(if (expanded) R.string.action_hide else R.string.action_show)) }
                    TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
                }
            }
            if (expanded) {
                Text(
                    journal.takeLast(120).joinToString("\n"),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}
