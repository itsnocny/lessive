package dev.nocny.lessive.data

import android.content.Context
import dev.nocny.lessive.R

/** Starting points for the dial editor. */
object Profiles {

    /** A dial nobody has mapped yet: numbered positions to rename once checked. */
    fun blank(model: String, positions: Int = 15) = MachineProfile(
        model = model,
        programs = (1..positions).map { DialProgram(selector = it, name = "Position $it") },
    )

    /**
     * Candy CSW 475D/5-S washer-dryer, measured position by position on a real unit
     * (https://github.com/Gonvar/candy-csw-nfc, README §7). Names are descriptive, not the
     * dial's printed labels, and are written in the phone's language when the preset is loaded.
     */
    fun csw475d(context: Context) = MachineProfile(
        model = "CSW 475D/5-S",
        spinLabels = listOf("Sans", "400", "800", "1000", "1400"),
        verified = true,
        programs = listOf(
            DialProgram(1, context.getString(R.string.preset_daily_59), ProgramKind.Everyday, 59),
            DialProgram(2, context.getString(R.string.preset_long_soak), ProgramKind.Intensive, 549),
            DialProgram(3, context.getString(R.string.preset_cotton), ProgramKind.Intensive, 149),
            DialProgram(4, context.getString(R.string.preset_cotton_intensive), ProgramKind.Intensive, 156),
            DialProgram(5, context.getString(R.string.preset_mixed_75), ProgramKind.Everyday, 75),
            DialProgram(6, context.getString(R.string.preset_mixed_54), ProgramKind.Everyday, 54),
            DialProgram(7, context.getString(R.string.preset_quick_10), ProgramKind.Quick, 10, forcedTemperature = 0, forcedSpin = 1),
            DialProgram(8, context.getString(R.string.preset_quick_28), ProgramKind.Quick, 28, forcedTemperature = 0, forcedSpin = 3),
            DialProgram(9, context.getString(R.string.preset_program_136), ProgramKind.Intensive, 136),
            DialProgram(10, context.getString(R.string.preset_program_48), ProgramKind.Everyday, 48),
            DialProgram(11, context.getString(R.string.preset_wool), ProgramKind.Delicate, 120, forcedTemperature = 0, forcedSpin = 0),
            DialProgram(12, context.getString(R.string.preset_delicate), ProgramKind.Delicate, 128, forcedTemperature = 0, forcedSpin = 0),
            DialProgram(13, context.getString(R.string.preset_delicate_2), ProgramKind.Delicate, 128, forcedTemperature = 0, forcedSpin = 0),
            DialProgram(14, context.getString(R.string.preset_dry_iron), ProgramKind.Dry, 59),
            DialProgram(15, context.getString(R.string.preset_program_136_2), ProgramKind.Intensive, 136),
        ),
    )

    val default = blank("EY 12102DE/1-47")
}
