package dev.nocny.lessive.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.R
import dev.nocny.lessive.data.DataPack
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.data.MachineProfile
import dev.nocny.lessive.data.Profiles
import dev.nocny.lessive.data.ProgramKind
import dev.nocny.lessive.data.isFrench
import dev.nocny.lessive.ui.components.ConnectedChoice
import dev.nocny.lessive.ui.components.ProgramAvatar
import dev.nocny.lessive.ui.components.spinLabels
import dev.nocny.lessive.ui.components.style
import dev.nocny.lessive.ui.components.temperatureLabels

private val SpinPresets = mapOf(
    "1000" to listOf("Sans", "400", "600", "800", "1000"),
    "1200" to listOf("Sans", "400", "800", "1000", "1200"),
    "1400" to listOf("Sans", "400", "800", "1000", "1400"),
)

@Composable
fun DialEditorScreen(
    initial: MachineProfile,
    dataPack: DataPack?,
    writeBlocked: Boolean,
    onBack: () -> Unit,
    onSave: (MachineProfile) -> Unit,
    onTry: (DialProgram) -> Unit,
) {
    var profile by remember { mutableStateOf(initial) }
    var editing by remember { mutableStateOf<DialProgram?>(null) }
    var confirmPreset by remember { mutableStateOf(false) }
    val context = LocalContext.current

    fun update(p: MachineProfile) {
        profile = p
        onSave(p)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dial_settings)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val next = (profile.programs.maxOfOrNull { it.selector } ?: 0) + 1
                    editing = DialProgram(next, context.getString(R.string.position_n, next), id = "custom-$next-${System.currentTimeMillis()}")
                },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.position_fab)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = profile.model,
                            onValueChange = { update(profile.copy(model = it)) },
                            label = { Text(stringResource(R.string.model)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(stringResource(R.string.max_spin), style = MaterialTheme.typography.titleSmall)
                        val presetKeys = SpinPresets.keys.toList()
                        ConnectedChoice(
                            options = presetKeys.map { context.getString(R.string.rpm, it) },
                            selected = presetKeys.indexOfFirst { SpinPresets[it] == profile.spinLabels },
                            onSelect = { update(profile.copy(spinLabels = SpinPresets.getValue(presetKeys[it]))) },
                        )
                        SwitchRow(
                            stringResource(R.string.dial_verified),
                            stringResource(R.string.dial_verified_text),
                            profile.verified,
                        ) { update(profile.copy(verified = it)) }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.dial_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
            val sorted = profile.programs.sortedBy { it.selector }
            itemsIndexed(sorted, key = { _, p -> p.key }) { index, program ->
                SegmentedListItem(
                    onClick = { editing = program },
                    shapes = ListItemDefaults.segmentedShapes(index, sorted.size),
                    leadingContent = { ProgramAvatar(program, size = 40.dp) },
                    trailingContent = { Icon(Icons.Rounded.Edit, stringResource(R.string.action_edit)) },
                    supportingContent = { Text(context.programSummary(program, profile)) },
                ) { Text(program.name) }
            }
            dataPack?.machineFor(profile.model, profile.applianceCode)?.let { official ->
                item {
                    TextButton(onClick = { update(official) }) { Text(stringResource(R.string.reload_official, official.model)) }
                }
            }
            item {
                TextButton(onClick = { confirmPreset = true }) { Text(stringResource(R.string.load_csw)) }
            }
        }
    }

    editing?.let { program ->
        PositionSheet(
            program = program,
            profile = profile,
            dataPack = dataPack,
            isNew = profile.programs.none { it.key == program.key },
            writeBlocked = writeBlocked,
            onDismiss = { editing = null },
            onSave = { saved ->
                update(profile.copy(programs = (profile.programs.filterNot { it.key == program.key } + saved).sortedBy { it.selector }))
                editing = null
            },
            onDelete = {
                update(profile.copy(programs = profile.programs.filterNot { it.key == program.key }))
                editing = null
            },
            onTry = { onTry(it) },
        )
    }

    if (confirmPreset) {
        AlertDialog(
            onDismissRequest = { confirmPreset = false },
            title = { Text(stringResource(R.string.replace_dial_title)) },
            text = { Text(stringResource(R.string.replace_dial_text)) },
            confirmButton = { TextButton(onClick = { confirmPreset = false; update(Profiles.csw475d(context)) }) { Text(stringResource(R.string.action_replace)) } },
            dismissButton = { TextButton(onClick = { confirmPreset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PositionSheet(
    program: DialProgram,
    profile: MachineProfile,
    dataPack: DataPack?,
    isNew: Boolean,
    writeBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (DialProgram) -> Unit,
    onDelete: () -> Unit,
    onTry: (DialProgram) -> Unit,
) {
    var draft by remember { mutableStateOf(program) }
    var duration by remember { mutableStateOf(program.durationMinutes?.toString().orEmpty()) }
    val result = draft.copy(durationMinutes = duration.toIntOrNull()?.takeIf { it > 0 })
    val context = LocalContext.current
    val french = context.isFrench()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgramAvatar(draft, size = 56.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(stringResource(R.string.position_n, draft.selector), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(draft.name.ifBlank { stringResource(R.string.untitled) }, style = MaterialTheme.typography.headlineSmallEmphasized)
                }
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it, catalogKey = null) },
                label = { Text(stringResource(R.string.program_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            dataPack?.let { pack ->
                val suggestions = pack.searchPrograms(draft.name).take(8)
                if (suggestions.isNotEmpty()) {
                    Text(stringResource(R.string.official_names), style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s ->
                            val name = if (french) s.fr else s.en
                            AssistChip(onClick = { draft = draft.copy(name = name, catalogKey = s.key) }, label = { Text(name) })
                        }
                    }
                }
            }
            Text(stringResource(R.string.type), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProgramKind.entries.forEach { kind ->
                    FilterChip(selected = draft.kind == kind, onClick = { draft = draft.copy(kind = kind) }, label = { Text(kind.style().label) })
                }
            }
            OutlinedTextField(
                value = duration,
                onValueChange = { duration = it.filter(Char::isDigit).take(4) },
                label = { Text(stringResource(R.string.shown_duration)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(stringResource(R.string.forced_temp_title), stringResource(R.string.forced_temp_text), draft.forcedTemperature != null) {
                draft = draft.copy(forcedTemperature = if (it) 0 else null)
            }
            draft.forcedTemperature?.let { t ->
                ConnectedChoice(context.temperatureLabels(profile), t, { draft = draft.copy(forcedTemperature = it) })
            }
            SwitchRow(stringResource(R.string.forced_spin_title), stringResource(R.string.forced_spin_text), draft.forcedSpin != null) {
                draft = draft.copy(forcedSpin = if (it) 0 else null)
            }
            draft.forcedSpin?.let { s ->
                ConnectedChoice(context.spinLabels(profile), s, { draft = draft.copy(forcedSpin = it) })
            }
            Spacer(Modifier.height(4.dp))
            FilledTonalButton(onClick = { onTry(result) }, enabled = !writeBlocked, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.try_on_machine))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!isNew) OutlinedButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_delete)) }
                Button(onClick = { onSave(result) }, enabled = draft.name.isNotBlank(), modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
