package dev.nocny.lessive.data

import kotlinx.serialization.Serializable

@Serializable
enum class ProgramKind { Everyday, Quick, Delicate, Intensive, Dry, Other }

/** One position of the machine's dial, i.e. one value of the START selector byte. */
@Serializable
data class DialProgram(
    val selector: Int,
    val name: String,
    val kind: ProgramKind = ProgramKind.Everyday,
    val durationMinutes: Int? = null,
    /** Set when the machine ignores the temperature sent and uses its own. */
    val forcedTemperature: Int? = null,
    /** Set when the machine ignores the spin sent and uses its own. */
    val forcedSpin: Int? = null,
    /** Link to an official program name in the optional data pack. */
    val catalogKey: String? = null,
    /** Unique within a profile; several programs can share a selector (Rapid 14/30/44). */
    val id: String = "",
    // Ranges from the machine's official definition, when known.
    val temperatureDefault: Int? = null,
    val temperatureMax: Int? = null,
    val spinDefault: Int? = null,
    val spinMax: Int? = null,
    val soilMin: Int? = null,
    val soilMax: Int? = null,
    val soilDefault: Int? = null,
    /** Duration in minutes at the lowest, middle and highest soil level. */
    val durationsBySoil: List<Int> = emptyList(),
    /** The machine runs this program with its own settings only (e.g. drum self-clean). */
    val locked: Boolean = false,
) {
    val key: String get() = id.ifEmpty { "$selector" }

    fun durationFor(soil: Int): Int? {
        if (durationsBySoil.size < 3 || soilMin == null || soilMax == null) return durationMinutes
        return when {
            soil <= soilMin -> durationsBySoil[0]
            soil >= soilMax -> durationsBySoil[2]
            else -> durationsBySoil[1]
        }
    }
}

@Serializable
data class MachineProfile(
    val model: String,
    val programs: List<DialProgram>,
    /** Label for each temperature index the machine accepts (index = list position). */
    val temperatureLabels: List<String> = DefaultTemperatures,
    /** Label for each spin index the machine accepts (index = list position). */
    val spinLabels: List<String> = listOf("Sans", "400", "800", "1000", "1200"),
    /** True once the dial mapping was checked against the machine's display. */
    val verified: Boolean = false,
    val applianceCode: String? = null,
    val interfaceType: String? = null,
) {
    fun program(key: String) = programs.firstOrNull { it.key == key }

    fun programFor(settings: WashSettings) =
        settings.programId.takeIf { it.isNotEmpty() }?.let(::program)
            ?: programs.firstOrNull { it.selector == settings.selector }

    companion object {
        val DefaultTemperatures = listOf("Froid", "20°", "30°", "40°", "60°")
    }
}

@Serializable
data class WashSettings(
    val selector: Int,
    val temperature: Int,
    val spin: Int,
    val soil: Int = 1,
    val extraRinse: Boolean = false,
    val delayHalfHours: Int? = null,
    val programId: String = "",
)

@Serializable
data class Favorite(val id: String, val name: String, val settings: WashSettings)

@Serializable
enum class Outcome { Started, NoAnswer, Interrupted, Refused }

@Serializable
data class HistoryEntry(
    val at: Long,
    val programName: String,
    val settings: WashSettings,
    val outcome: Outcome,
)
