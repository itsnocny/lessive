package dev.nocny.lessive.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.nocny.lessive.R
import dev.nocny.lessive.data.DialProgram
import dev.nocny.lessive.data.Favorite
import dev.nocny.lessive.data.Habits
import dev.nocny.lessive.data.MachineProfile
import dev.nocny.lessive.data.WashSettings
import dev.nocny.lessive.nfc.NfcHub
import dev.nocny.lessive.ui.components.ProgramAvatar
import dev.nocny.lessive.ui.components.daysAgo
import dev.nocny.lessive.ui.components.describe
import dev.nocny.lessive.ui.components.formatDuration
import dev.nocny.lessive.ui.components.formatHour
import dev.nocny.lessive.ui.components.spinText
import dev.nocny.lessive.ui.components.temperatureLabels

private data class Usual(val name: String, val settings: WashSettings, val favorite: Favorite? = null)

@Composable
fun HomeScreen(
    profile: MachineProfile,
    favorites: List<Favorite>,
    habits: Habits,
    nfc: NfcHub.Availability,
    writeBlocked: Boolean,
    canAdvise: Boolean,
    outerPadding: PaddingValues,
    onOpenProgram: (DialProgram) -> Unit,
    onStart: (String, WashSettings) -> Unit,
    onRemoveFavorite: (Favorite) -> Unit,
    onEditDial: () -> Unit,
    onAdvise: () -> Unit,
    onOpenMachine: () -> Unit,
) {
    val context = LocalContext.current
    val last = habits.last
    val usual = remember(favorites, habits) {
        val fromFavorites = favorites.map { Usual(it.name, it.settings, it) }
        val fromHistory = habits.usual(4).map { Usual(profile.programFor(it)!!.name, it) }
            .filter { u -> fromFavorites.none { it.settings.programId == u.settings.programId } }
        (fromFavorites + fromHistory).take(maxOf(4, fromFavorites.size))
    }
    val autoClean = profile.programs.firstOrNull { it.catalogKey?.contains("AUTO_CLEAN") == true }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = outerPadding.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        item { Header(profile, nfc) }
        if (nfc != NfcHub.Availability.Ready) item { NfcBanner(nfc) }
        if (writeBlocked) item {
            Banner(
                icon = { Icon(Icons.Rounded.Lock, null) },
                title = stringResource(R.string.blocked_title),
                text = stringResource(R.string.blocked_home),
            )
        }
        if (!profile.verified) item {
            Banner(
                icon = { Icon(Icons.Rounded.Tune, null) },
                title = stringResource(R.string.dial_unverified_title),
                text = stringResource(R.string.dial_unverified_text),
                action = stringResource(R.string.action_configure) to onEditDial,
            )
        }
        if (last != null) item {
            val program = profile.programFor(last)!!
            Relaunch(program, last, profile, enabled = !writeBlocked) { onStart(program.name, last) }
        }
        if (usual.isNotEmpty()) {
            item {
                Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        stringResource(R.string.usual_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.usual_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // A card left alone on its row takes the full width instead of leaving a hole.
            items(usual.chunked(2)) { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { u ->
                        UsualCard(
                            usual = u,
                            profile = profile,
                            enabled = !writeBlocked,
                            onOpen = { profile.programFor(u.settings)?.let(onOpenProgram) },
                            onStart = { onStart(u.name, u.settings) },
                            onRemove = u.favorite?.let { { onRemoveFavorite(it) } },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        if (canAdvise) item {
            Shortcut(
                title = stringResource(R.string.advisor_title),
                subtitle = stringResource(R.string.advisor_subtitle),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onAdvise,
            ) {
                Box(
                    Modifier.size(44.dp).background(MaterialTheme.colorScheme.onTertiaryContainer, MaterialShapes.Flower.toShape()),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AutoFixHigh, null, tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(22.dp)) }
            }
        }
        if (autoClean != null) item {
            val lastClean = habits.lastRunOf(autoClean)
            Shortcut(
                title = stringResource(R.string.autoclean_shortcut),
                subtitle = lastClean?.let { stringResource(R.string.last_run, context.daysAgo(it)) } ?: stringResource(R.string.never_run),
                container = MaterialTheme.colorScheme.surfaceContainer,
                content = MaterialTheme.colorScheme.onSurface,
                onClick = onOpenMachine,
            ) {
                ProgramAvatar(
                    autoClean,
                    size = 44.dp,
                    container = MaterialTheme.colorScheme.surfaceContainerHigh,
                    content = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item { SectionTitle(stringResource(R.string.all_programs)) }
        // One entry per dial position: Rapide 14/30/44 is one program with a duration choice.
        val positions = profile.programs.groupBy { it.selector }.values.toList()
        itemsIndexed(positions, key = { _, group -> group.first().selector }) { index, group ->
            val program = group.first()
            SegmentedListItem(
                onClick = { onOpenProgram(program) },
                shapes = ListItemDefaults.segmentedShapes(index, positions.size),
                leadingContent = { ProgramAvatar(program) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
                supportingContent = {
                    val text = if (group.size == 1) context.programSummary(program, profile)
                    else context.getString(R.string.position_n, program.selector) + " · " +
                        group.mapNotNull { it.durationMinutes }.joinToString(", ", postfix = " min")
                    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            ) {
                Text(if (group.size == 1) program.name else program.name.substringBefore(' '), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun Header(profile: MachineProfile, nfc: NfcHub.Availability) {
    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.home_brand, profile.model.substringBefore('/')), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLargeEmphasized)
        }
        val ready = nfc == NfcHub.Availability.Ready
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Nfc,
                    null,
                    tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (ready) R.string.nfc_ready else R.string.nfc_off), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Relaunch(program: DialProgram, settings: WashSettings, profile: MachineProfile, enabled: Boolean, onStart: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgramAvatar(program, size = 56.dp, container = colors.primary, content = colors.onPrimary)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.relaunch_last), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
                    Text(program.name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                context.temperatureLabels(profile).getOrNull(settings.temperature)?.let { Pill(it) }
                context.spinText(profile, settings.spin)?.let { Pill(it) }
                program.durationFor(settings.soil)?.let {
                    Pill(stringResource(R.string.ends_around_short, context.formatHour(System.currentTimeMillis() + it * 60_000L)))
                }
            }
            Button(
                onClick = onStart,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) {
                Icon(Icons.Rounded.PlayArrow, null, Modifier.padding(end = 8.dp))
                Text(stringResource(R.string.action_launch), style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }
}

@Composable
private fun Pill(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier
        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f), CircleShape)
        .padding(horizontal = 12.dp, vertical = 6.dp),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UsualCard(
    usual: Usual,
    profile: MachineProfile,
    enabled: Boolean,
    onOpen: () -> Unit,
    onStart: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var confirmRemove by remember { mutableStateOf(false) }
    val program = profile.programFor(usual.settings)
    val context = LocalContext.current
    val removeLabel = stringResource(R.string.remove_favorite)
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = modifier.combinedClickable(
            onClick = onOpen,
            onLongClick = onRemove?.let { { confirmRemove = true } },
            onLongClickLabel = onRemove?.let { removeLabel },
        ),
    ) {
        Column(Modifier.heightIn(min = 120.dp).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                if (program != null) ProgramAvatar(program, size = 44.dp)
                Spacer(Modifier.weight(1f))
                FilledIconButton(onClick = onStart, enabled = enabled, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.PlayArrow, stringResource(R.string.launch_named, usual.name), Modifier.size(18.dp))
                }
            }
            Column {
                Text(usual.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    context.describe(profile, usual.settings, short = true),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    if (confirmRemove && onRemove != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.remove_favorite_title, usual.name)) },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove() }) { Text(stringResource(R.string.action_remove)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.action_keep)) } },
        )
    }
}

@Composable
private fun Shortcut(
    title: String,
    subtitle: String,
    container: Color,
    content: Color,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            leading()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = content.copy(alpha = 0.85f))
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
        }
    }
}

fun Context.programSummary(program: DialProgram, profile: MachineProfile): String = buildList {
    add(getString(R.string.position_n, program.selector))
    val durations = program.durationsBySoil.distinct()
    when {
        durations.size > 1 -> add(getString(R.string.duration_range, formatDuration(durations.min()), formatDuration(durations.max())))
        else -> (durations.firstOrNull() ?: program.durationMinutes)?.let { add(formatDuration(it)) }
    }
    if (program.locked) add(getString(R.string.settings_fixed))
    if (program.forcedTemperature != null || program.forcedSpin != null) {
        val t = program.forcedTemperature?.let { temperatureLabels(profile).getOrNull(it) }
        val s = program.forcedSpin?.let { spinText(profile, it) }
        add(getString(R.string.fixed_suffix, listOfNotNull(t, s).joinToString(" · ")))
    }
}.joinToString(" · ")

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp),
    )
}

@Composable
private fun NfcBanner(nfc: NfcHub.Availability) {
    val context = LocalContext.current
    if (nfc == NfcHub.Availability.Missing) {
        Banner({ Icon(Icons.Rounded.Nfc, null) }, stringResource(R.string.no_nfc_title), stringResource(R.string.no_nfc_text))
    } else {
        Banner(
            { Icon(Icons.Rounded.Nfc, null) }, stringResource(R.string.nfc_disabled_title), stringResource(R.string.nfc_disabled_text),
            action = stringResource(R.string.action_enable) to { context.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
        )
    }
}

@Composable
fun Banner(
    icon: @Composable () -> Unit,
    title: String,
    text: String,
    action: Pair<String, () -> Unit>? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
        }
    }
}
