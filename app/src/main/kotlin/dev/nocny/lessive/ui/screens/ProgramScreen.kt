package dev.nocny.lessive.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.R
import dev.nocny.lessive.data.CatalogProgram
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.data.Habits
import dev.nocny.lessive.data.MachineProfile
import dev.nocny.lessive.data.WashSettings
import dev.nocny.lessive.data.description
import dev.nocny.lessive.data.isFrench
import dev.nocny.lessive.data.minuteOfDay
import dev.nocny.lessive.data.nextOccurrence
import dev.nocny.lessive.ui.components.ConnectedChoice
import dev.nocny.lessive.ui.components.ProgramAvatar
import dev.nocny.lessive.ui.components.formatDuration
import dev.nocny.lessive.ui.components.formatHour
import dev.nocny.lessive.ui.components.spinLabels
import dev.nocny.lessive.ui.components.spinText
import dev.nocny.lessive.ui.components.style
import dev.nocny.lessive.ui.components.temperatureLabels
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

// Soil levels as the machine counts them: 1..4 on 2D washers.
private val SoilLabels = mapOf(0 to R.string.soil_light, 1 to R.string.soil_light, 2 to R.string.soil_normal, 3 to R.string.soil_heavy, 4 to R.string.soil_max)

private const val MINUTE = 60_000L

/** Values the user may pick for one setting, from the official range when known. */
private data class Range(val values: List<Int>) {
    val fixed get() = values.size <= 1
    fun pick(value: Int) = if (value in values) value else values.lastOrNull { it <= value } ?: values.firstOrNull() ?: 0
}

private fun temperatureRange(p: DialProgram, profile: MachineProfile) = Range(
    when {
        p.locked -> listOfNotNull(p.temperatureDefault)
        p.forcedTemperature != null -> listOf(p.forcedTemperature)
        p.temperatureMax != null -> (0..p.temperatureMax.coerceAtMost(profile.temperatureLabels.lastIndex)).toList()
        else -> profile.temperatureLabels.indices.toList()
    }
)

private fun spinRange(p: DialProgram, profile: MachineProfile) = Range(
    when {
        p.locked -> listOfNotNull(p.spinDefault)
        p.forcedSpin != null -> listOf(p.forcedSpin)
        p.spinMax != null -> (0..p.spinMax.coerceAtMost(profile.spinLabels.lastIndex)).toList()
        else -> profile.spinLabels.indices.toList()
    }
)

private fun soilRange(p: DialProgram) = Range(
    when {
        p.soilMin != null && p.soilMax != null -> (p.soilMin..p.soilMax).toList()
        else -> (0..3).toList() // Unknown machine: the CSW 475D range.
    }
)

/**
 * The machine counts a delay in half hours from now. Converting an end time to half hours
 * rounds it, so the wash may end up to 15 minutes away from the time picked.
 */
private fun delayFor(endAt: Long, now: Long, duration: Int) =
    (((endAt - now) / MINUTE - duration) / 30.0).roundToInt().coerceIn(1, 48)

@Composable
fun ProgramScreen(
    program: DialProgram,
    profile: MachineProfile,
    initial: WashSettings,
    catalog: (DialProgram) -> CatalogProgram?,
    habits: Habits,
    writeBlocked: Boolean,
    onBack: () -> Unit,
    onStart: (String, WashSettings) -> Unit,
    onSaveFavorite: (String, WashSettings) -> Unit,
) {
    // Programs sharing a dial position (Rapide 14/30/44) are one program with a duration choice.
    val variants = profile.programs.filter { it.selector == program.selector }
    var currentKey by rememberSaveable { mutableStateOf(program.key) }
    val current = profile.program(currentKey) ?: program

    val temps = temperatureRange(current, profile)
    val spins = spinRange(current, profile)
    val soils = soilRange(current)
    var wantedTemperature by rememberSaveable { mutableStateOf(initial.temperature) }
    var wantedSpin by rememberSaveable { mutableStateOf(initial.spin) }
    var wantedSoil by rememberSaveable { mutableStateOf(initial.soil) }
    val temperature = temps.pick(wantedTemperature)
    val spin = spins.pick(wantedSpin)
    val soil = soils.pick(wantedSoil)

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    val duration = current.durationFor(soil)
    val minutes = duration ?: 0
    val usualEnds = remember(habits) { habits.usualEndTimes() }
    var endMinute by rememberSaveable { mutableStateOf<Int?>(null) }
    val endAt = endMinute?.let { nextOccurrence(it, now + (minutes + 15) * MINUTE) }
    val delayHalfHours = endAt?.let { delayFor(it, now, minutes) }
    val startAt = now + (delayHalfHours ?: 0) * 30 * MINUTE

    var naming by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val settings = WashSettings(
        selector = current.selector,
        temperature = temperature,
        spin = spin,
        soil = soil,
        delayHalfHours = delayHalfHours,
        programId = current.key,
    )

    Scaffold(
        bottomBar = {
            LaunchBar(
                headline = when {
                    duration != null -> stringResource(R.string.ends_around, context.formatHour(startAt + duration * MINUTE, now))
                    delayHalfHours != null -> stringResource(R.string.starts_around, context.formatHour(startAt, now))
                    else -> stringResource(R.string.position_n, current.selector)
                },
                summary = listOfNotNull(
                    context.temperatureLabels(profile).getOrNull(temperature),
                    context.spinText(profile, spin),
                    duration?.let(::formatDuration),
                ).joinToString(" · "),
                action = stringResource(
                    when {
                        writeBlocked -> R.string.blocked
                        delayHalfHours != null -> R.string.action_schedule
                        else -> R.string.action_launch
                    }
                ),
                enabled = !writeBlocked,
                onClick = { onStart(current.name, settings) },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).statusBarsPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back)) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { naming = true }) { Icon(Icons.Rounded.StarOutline, stringResource(R.string.add_favorite)) }
            }
            Header(current, catalog(current)?.description(context.isFrench()))

            if (variants.size > 1) {
                Section(stringResource(R.string.duration)) {
                    ConnectedChoice(
                        variants.map { v -> v.durationMinutes?.let(::formatDuration) ?: v.name },
                        variants.indexOfFirst { it.key == current.key },
                        { currentKey = variants[it].key },
                    )
                }
            }
            if (current.locked) {
                Section(null) { Forced(stringResource(R.string.own_settings)) }
            } else {
                Section(stringResource(R.string.temperature)) {
                    val labels = context.temperatureLabels(profile)
                    if (temps.fixed) {
                        Forced(stringResource(R.string.forced_temperature, labels.getOrElse(temperature) { "?" }))
                    } else {
                        ConnectedChoice(labels, temperature, { wantedTemperature = it }, isAllowed = { it in temps.values })
                    }
                }
                Section(stringResource(R.string.spin_title)) {
                    if (spins.fixed) {
                        Forced(stringResource(R.string.forced_spin, context.spinText(profile, spin).orEmpty().replaceFirstChar { it.uppercase() }))
                    } else {
                        ConnectedChoice(context.spinLabels(profile), spin, { wantedSpin = it }, isAllowed = { it in spins.values })
                    }
                }
                if (!soils.fixed) {
                    Section(stringResource(R.string.soil)) {
                        ConnectedChoice(soils.values.map { stringResource(SoilLabels.getValue(it)) }, soils.values.indexOf(soil), { wantedSoil = soils.values[it] })
                    }
                }
            }
            Section(stringResource(R.string.when_title), hint = stringResource(R.string.usual_times).takeIf { usualEnds.isNotEmpty() }) {
                When(
                    selected = endMinute,
                    usual = usualEnds,
                    now = now,
                    minutes = minutes,
                    knowsDuration = duration != null,
                    onSelect = { endMinute = it },
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (naming) {
        var name by remember { mutableStateOf(current.name) }
        AlertDialog(
            onDismissRequest = { naming = false },
            title = { Text(stringResource(R.string.new_favorite)) },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text(stringResource(R.string.name)) }) },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = { naming = false; onSaveFavorite(name.trim(), settings.copy(delayHalfHours = null)) }) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun When(
    selected: Int?,
    usual: List<Int>,
    now: Long,
    minutes: Int,
    knowsDuration: Boolean,
    onSelect: (Int?) -> Unit,
) {
    var custom by rememberSaveable { mutableStateOf<Int?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val chip = if (knowsDuration) R.string.end_chip else R.string.start_chip
    val choices = listOf<Int?>(null) + usual + listOfNotNull(custom?.takeIf { it !in usual })

    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        choices.forEach { minute ->
            ToggleButton(checked = selected == minute && !picking, onCheckedChange = { picking = false; onSelect(minute) }) {
                if (minute != null && minute in usual) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                }
                val earliest = now + ((if (knowsDuration) minutes else 0) + 15) * MINUTE
                Text(if (minute == null) stringResource(R.string.now) else stringResource(chip, context.formatHour(nextOccurrence(minute, earliest), now)))
            }
        }
        OutlinedToggleButton(checked = picking, onCheckedChange = { picking = it }) { Text(stringResource(R.string.other_time)) }
    }
    AnimatedVisibility(picking) {
        TimePicker(
            initial = custom ?: (minuteOfDay(now + (minutes + 60) * MINUTE) / 30 + 1) * 30 % 1440,
            now = now,
            minutes = minutes,
            knowsDuration = knowsDuration,
            onConfirm = {
                custom = it
                picking = false
                onSelect(it)
            },
        )
    }
    if (selected != null) {
        Text(
            stringResource(R.string.delay_unverified),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Half-hour steps, the delay unit of the machine. */
@Composable
private fun TimePicker(initial: Int, now: Long, minutes: Int, knowsDuration: Boolean, onConfirm: (Int) -> Unit) {
    val context = LocalContext.current
    var hour by rememberSaveable { mutableStateOf(initial / 60) }
    var half by rememberSaveable { mutableStateOf(initial % 60 >= 30) }
    val minuteOfDay = hour * 60 + if (half) 30 else 0
    val target = nextOccurrence(minuteOfDay, now)
    val fromNow = (target - now) / MINUTE
    val problem = when {
        fromNow < minutes + 15 -> stringResource(R.string.too_early, formatDuration(minutes))
        fromNow - minutes > 24 * 60 -> stringResource(R.string.over_24h)
        else -> null
    }

    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        delay(250) // let the expand animation finish so the whole picker is measured
        reveal.bringIntoView()
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).bringIntoViewRequester(reveal),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (knowsDuration) R.string.end_at_label else R.string.start_at_label), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                FilledTonalIconButton(onClick = { hour = (hour + 23) % 24 }) { Icon(Icons.Rounded.Remove, stringResource(R.string.previous_hour)) }
                Text(
                    "%02d".format(hour),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
                FilledTonalIconButton(onClick = { hour = (hour + 1) % 24 }) { Icon(Icons.Rounded.Add, stringResource(R.string.next_hour)) }
                Text(":", style = MaterialTheme.typography.headlineMediumEmphasized)
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(onClick = { half = !half }) {
                    Text(if (half) "30" else "00", style = MaterialTheme.typography.headlineSmallEmphasized)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    problem ?: if (knowsDuration) {
                        stringResource(R.string.picker_end, context.formatHour(target, now), context.formatHour(target - minutes * MINUTE, now))
                    } else {
                        stringResource(R.string.picker_start, context.formatHour(target, now))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = { onConfirm(minuteOfDay) }, enabled = problem == null) { Text(stringResource(R.string.action_ok)) }
            }
        }
    }
}

@Composable
private fun LaunchBar(headline: String, summary: String, action: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(headline, style = MaterialTheme.typography.titleLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                modifier = Modifier.height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(if (enabled) Icons.Rounded.PlayArrow else Icons.Rounded.Lock, null, Modifier.padding(end = 8.dp))
                Text(action, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }
}

@Composable
private fun Header(program: DialProgram, description: String?) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgramAvatar(program, size = 64.dp)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(program.name, style = MaterialTheme.typography.headlineMediumEmphasized)
                Text(
                    stringResource(R.string.program_subtitle, program.selector, program.kind.style().label.lowercase()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        description?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Section(title: String?, hint: String? = null, content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (title != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(title, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    hint?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                Spacer(Modifier.height(10.dp))
            }
            content()
        }
    }
}

@Composable
private fun Forced(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
