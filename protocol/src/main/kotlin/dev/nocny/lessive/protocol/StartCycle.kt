package dev.nocny.lessive.protocol

/**
 * START_PROGRAM_CYCLE (action 0x0B): the command validated on a CSW 475D/5-S.
 *
 * Every value is the raw byte the machine expects (temperature and spin are indices,
 * not degrees or rpm). The firmware rejects out-of-range combinations on its own.
 *
 * @property delayHalfHours null to start now (encoded 0xFF), otherwise the number of
 *   half-hours from now. Whether the machine reads it as a start or a finish delay is
 *   not verified yet.
 */
data class StartCycle(
    val selector: Int,
    val temperature: Int,
    val spin: Int,
    val soil: Int = 0,
    val option1: Int = 0,
    val option2: Int = 0,
    val option3: Int = 0,
    val extraRinse: Int = 0,
    val delayHalfHours: Int? = null,
    val dry: Int = 0,
) {
    init {
        require(selector in 1..0xFE) { "selector $selector: 0 is OFF and makes the machine show E15" }
        listOf(temperature, spin, soil, option1, option2, option3, extraRinse, dry).forEach {
            require(it in 0..0xFF) { "byte out of range: $it" }
        }
        require(delayHalfHours == null || delayHalfHours in 1..MAX_DELAY_HALF_HOURS) {
            "delay must be 1..$MAX_DELAY_HALF_HOURS half-hours"
        }
    }

    /** The 18-byte NDEF external record written to file 0002. */
    fun toRecord(): ByteArray {
        val record = bytes(
            0xD4, // NDEF header: MB + ME + SR, TNF = external
            0x01, // type length
            0x0E, // payload length (14)
            0x02, // type: command
            COMMAND_MARKER,
            ACTION,
            selector, temperature, spin, soil, option1, option2, option3, extraRinse,
            delayHalfHours ?: 0xFF,
            dry,
            0, 0, // CRC
        )
        val crc = Crc16.compute(record, from = 4, to = 16)
        record[16] = (crc ushr 8).toByte()
        record[17] = crc.toByte()
        return record
    }

    companion object {
        const val ACTION = 0x0B
        const val COMMAND_MARKER = 0x80
        const val MAX_DELAY_HALF_HOURS = 48
    }
}
