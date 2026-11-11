package dev.nocny.lessive.data

import java.util.Calendar
import kotlin.math.roundToInt

/** Usage figures from the launch history. Only successful launches are counted. */
class Habits(private val history: List<HistoryEntry>, private val profile: MachineProfile) {
    private val started = history.filter { it.outcome == Outcome.Started && profile.programFor(it.settings) != null }

    /** The most recent launch, without its delay: relaunching means now. */
    val last: WashSettings? = started.firstOrNull()?.settings?.copy(delayHalfHours = null)

    /** Settings most often launched, one entry per program, most frequent first. */
    fun usual(limit: Int): List<WashSettings> =
        started.groupBy { profile.programFor(it.settings)!!.key }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<HistoryEntry>>> { it.value.size }.thenByDescending { it.value.first().at })
            .take(limit)
            .map { it.value.first().settings.copy(delayHalfHours = null) }

    /** Launches per program over the last [days] days. */
    fun counts(days: Int, now: Long = System.currentTimeMillis()): List<Pair<DialProgram, Int>> =
        started.filter { now - it.at <= days * 86_400_000L }
            .groupingBy { profile.programFor(it.settings)!! }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }

    fun lastRunOf(program: DialProgram): Long? = started.firstOrNull { profile.programFor(it.settings)?.key == program.key }?.at

    /**
     * End times picked more than once for delayed washes, as minutes after midnight rounded
     * to the half hour.
     */
    fun usualEndTimes(limit: Int = 2): List<Int> =
        started.mapNotNull { entry ->
            val delay = entry.settings.delayHalfHours ?: return@mapNotNull null
            val duration = profile.programFor(entry.settings)?.durationFor(entry.settings.soil) ?: return@mapNotNull null
            val minutes = minuteOfDay(entry.at + (delay * 30L + duration) * 60_000)
            ((minutes / 30.0).roundToInt() * 30) % 1440
        }
            .groupingBy { it }
            .eachCount()
            .filterValues { it >= 2 }
            .entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { it.key }
}

/** Next time the clock shows [minuteOfDay], never earlier than [notBefore]. */
fun nextOccurrence(minuteOfDay: Int, notBefore: Long): Long {
    val c = Calendar.getInstance().apply {
        timeInMillis = notBefore
        set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
        set(Calendar.MINUTE, minuteOfDay % 60)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (c.timeInMillis < notBefore) c.add(Calendar.DAY_OF_YEAR, 1)
    return c.timeInMillis
}

fun minuteOfDay(epochMs: Long): Int {
    val c = Calendar.getInstance().apply { timeInMillis = epochMs }
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}
