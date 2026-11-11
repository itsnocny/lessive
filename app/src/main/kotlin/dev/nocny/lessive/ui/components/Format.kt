package dev.nocny.lessive.ui.components

import android.content.Context
import dev.nocny.lessive.R
import dev.nocny.lessive.data.MachineProfile
import dev.nocny.lessive.data.WashSettings
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatDuration(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h %02d".format(minutes % 60)
}

fun Context.formatDateTime(epochMs: Long): String =
    SimpleDateFormat(getString(R.string.date_time_pattern), Locale.getDefault()).format(Date(epochMs))

/** "16 h 25" or "4:25 PM", prefixed with "tomorrow" when it falls on another day. */
fun Context.formatHour(epochMs: Long, now: Long = System.currentTimeMillis()): String {
    val text = SimpleDateFormat(getString(R.string.hour_pattern), Locale.getDefault()).format(Date(epochMs))
    val day = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_YEAR) }
    return if (day(epochMs) != day(now)) getString(R.string.tomorrow_at, text) else text
}

fun Context.daysAgo(epochMs: Long, now: Long = System.currentTimeMillis()): String =
    when (val days = ((now - epochMs) / 86_400_000L).toInt()) {
        0 -> getString(R.string.today)
        1 -> getString(R.string.yesterday)
        else -> resources.getQuantityString(R.plurals.days_ago, days, days)
    }

// Profiles store the labels the machine data uses ("Froid", "Sans"); everything else is a number.
fun Context.temperatureLabels(profile: MachineProfile) =
    profile.temperatureLabels.map { if (it == "Froid") getString(R.string.temp_cold) else it }

fun Context.spinLabels(profile: MachineProfile) =
    profile.spinLabels.map { if (it == "Sans") getString(R.string.spin_none) else it }

fun Context.spinText(profile: MachineProfile, index: Int): String? =
    profile.spinLabels.getOrNull(index)?.let { if (it == "Sans") getString(R.string.no_spin) else getString(R.string.rpm, it) }

/** "40° · 800 rpm · 1 h 55" */
fun Context.describe(profile: MachineProfile, settings: WashSettings, short: Boolean = false): String {
    val program = profile.programFor(settings)
    val spin = if (short) spinLabels(profile).getOrNull(settings.spin) else spinText(profile, settings.spin)
    return listOfNotNull(
        temperatureLabels(profile).getOrNull(settings.temperature),
        spin,
        program?.durationFor(settings.soil)?.let(::formatDuration),
    ).joinToString(" · ")
}
